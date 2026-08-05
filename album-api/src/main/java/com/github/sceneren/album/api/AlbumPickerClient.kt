package com.github.sceneren.album.api

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.github.sceneren.album.api.internal.file.AlbumFileMaterializer
import com.github.sceneren.album.api.internal.session.AlbumPendingCameraCapture
import com.github.sceneren.album.api.internal.session.AlbumPickerItemSource
import com.github.sceneren.album.api.internal.session.AlbumPickerSelection
import com.github.sceneren.album.api.internal.session.AlbumPickerSelectionReducer
import com.github.sceneren.album.api.internal.session.AlbumPickerSessionState
import com.github.sceneren.album.api.internal.session.AlbumPickerSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.zibin.luban.api.Luban
import java.io.File
import java.io.IOException
import java.util.UUID

/** UI 模块共享的选择会话数据。 */
data class AlbumPickerSessionSnapshot(
    val sessionId: String,
    val selectedItems: List<AlbumMedia>,
    val cameraItems: List<AlbumMedia>,
    val selectedUris: Set<Uri>,
    val bucketId: Long,
    val previewUri: Uri?,
    val hasPendingCamera: Boolean,
)

/** 待交给系统相机 Activity Result Contract 的输出文件。 */
data class AlbumCameraCapture(
    val uri: Uri,
    val filePath: String,
    val mediaType: AlbumMediaType,
)

/** 相机 Activity Result 的统一包装。 */
class AlbumCameraLauncher internal constructor(
    private val activity: ComponentActivity,
    private val client: AlbumPickerClient,
    private val sessionId: String,
    private val photoLauncher: androidx.activity.result.ActivityResultLauncher<Uri>,
    private val videoLauncher: androidx.activity.result.ActivityResultLauncher<Uri>,
    private val onResult: (Result<AlbumPickerSessionSnapshot>) -> Unit,
) {
    /** 注册后可多次调用；每次调用都会先把待写入 URI 持久化。 */
    fun launch(mediaType: AlbumMediaType) {
        activity.lifecycleScope.launch {
            val captureType = when (mediaType) {
                AlbumMediaType.IMAGE -> AlbumMediaType.IMAGE
                AlbumMediaType.VIDEO -> AlbumMediaType.VIDEO
            }
            val prepared = client.prepareCamera(sessionId, captureType)
            prepared.fold(
                onSuccess = { capture ->
                    if (captureType == AlbumMediaType.IMAGE) {
                        photoLauncher.launch(capture.uri)
                    } else {
                        videoLauncher.launch(capture.uri)
                    }
                },
                onFailure = { failure -> onResult(Result.failure(failure)) },
            )
        }
    }
}

/** 相册 UI 使用的会话、相机和最终文件处理器。 */
class AlbumPickerClient internal constructor(
    private val context: Context,
    private val externalRootOverride: File? = null,
) {
    private val appContext = context.applicationContext
    private val store = AlbumPickerSessionStore(appContext)
    private val materializer = AlbumFileMaterializer(
        appContext,
        appContext.contentResolver,
        externalRootOverride ?: appContext.getExternalFilesDir(null),
    )
    fun openSession(config: AlbumPickerConfig, sessionId: String? = null): AlbumPickerSessionSnapshot {
        val state = sessionId?.let(store::load)
            ?.takeIf { it.config == config }
            ?: store.create(config)
        return state.toSnapshot()
    }

    fun snapshot(sessionId: String): AlbumPickerSessionSnapshot =
        requireNotNull(store.load(sessionId)) { "相册选择会话不存在: $sessionId" }.toSnapshot()

    suspend fun toggleSelection(
        sessionId: String,
        media: AlbumMedia,
    ): Result<AlbumPickerSessionSnapshot> = runCatching {
        withContext(Dispatchers.IO) {
            val state = requireState(sessionId)
            val item = media.toSelection()
            val next = state.copy(
                selected = AlbumPickerSelectionReducer.toggle(
                    selected = state.selected,
                    item = item,
                    maxSelectionCount = state.config.maxSelectionCount,
                ),
                previewUri = media.uri,
            )
            store.save(next)
            next.toSnapshot()
        }
    }

    suspend fun setBucket(sessionId: String, bucketId: Long): Result<AlbumPickerSessionSnapshot> =
        runCatching {
            withContext(Dispatchers.IO) {
                val state = requireState(sessionId).copy(bucketId = bucketId)
                store.save(state)
                state.toSnapshot()
            }
        }

    suspend fun prepareCamera(
        sessionId: String,
        mediaType: AlbumMediaType,
    ): Result<AlbumCameraCapture> = runCatching {
        withContext(Dispatchers.IO) {
            val state = requireState(sessionId)
            check(state.pendingCamera == null) { "已有一次拍摄正在进行" }
            val root = externalRoot()
            val directory = File(root, CAMERA_DIRECTORY).apply {
                if (!exists() && !mkdirs()) throw IOException("无法创建拍摄目录")
            }
            val suffix = if (mediaType == AlbumMediaType.IMAGE) ".jpg" else ".mp4"
            val file = File(directory, "${UUID.randomUUID()}$suffix")
            val uri = fileUri(file)
            store.save(
                state.copy(
                    pendingCamera = AlbumPendingCameraCapture(
                        uri = uri,
                        filePath = file.absolutePath,
                        mediaType = mediaType,
                    ),
                ),
            )
            AlbumCameraCapture(uri, file.absolutePath, mediaType)
        }
    }

    suspend fun completeCamera(
        sessionId: String,
        success: Boolean,
    ): Result<AlbumPickerSessionSnapshot> = runCatching {
        withContext(Dispatchers.IO) {
            val state = requireState(sessionId)
            val pending = requireNotNull(state.pendingCamera) { "没有待完成的拍摄" }
            val file = File(pending.filePath)
            if (!success || !file.exists() || file.length() <= 0L) {
                file.delete()
                val cleared = state.copy(pendingCamera = null)
                store.save(cleared)
                return@withContext cleared.toSnapshot()
            }

            val item = AlbumPickerSelection(
                uri = pending.uri,
                mediaType = pending.mediaType,
                displayName = file.name,
                mimeType = if (pending.mediaType == AlbumMediaType.IMAGE) {
                    "image/jpeg"
                } else {
                    "video/mp4"
                },
                sizeBytes = file.length(),
                width = null,
                height = null,
                durationMillis = null,
                source = AlbumPickerItemSource.CAMERA,
                filePath = pending.filePath,
            )
            val added = AlbumPickerSelectionReducer.addCamera(
                selected = state.selected,
                cameraItems = state.cameraItems,
                cameraItem = item,
                config = state.config,
            )
            val next = state.copy(
                selected = added.selected,
                cameraItems = added.cameraItems,
                pendingCamera = null,
                previewUri = item.uri,
            )
            store.save(next)
            next.toSnapshot()
        }
    }

    suspend fun confirm(sessionId: String): Result<AlbumPickerResult> = runCatching {
        withContext(Dispatchers.IO) {
            val state = requireState(sessionId)
            check(state.pendingCamera == null) { "拍摄尚未完成" }
            check(state.selected.isNotEmpty()) { "至少选择一个媒体" }
            val copied = materializer.copyAll(
                state.selected.map { it.uri to it.displayName },
            )
            val compressedFiles = mutableListOf<File>()
            try {
                val policy = AlbumCompressionPolicy(state.config.compression)
                val items = state.selected.zip(copied).map { (selection, materialized) ->
                    val status = policy.statusFor(selection.mediaType, materialized.sizeBytes)
                    val resultFile = if (status == AlbumCompressionStatus.COMPRESSED) {
                        val outputDir = lubanDirectory()
                        val output = Luban.compress(File(materialized.filePath), outputDir)
                            .getOrElse { throw it }
                        compressedFiles += output
                        output
                    } else {
                        File(materialized.filePath)
                    }
                    AlbumPickerResultItem(
                        originalUri = selection.uri,
                        resultUri = fileUri(resultFile),
                        mediaType = selection.mediaType,
                        compressionStatus = status,
                        originalFilePath = materialized.originalFilePath,
                        filePath = resultFile.absolutePath,
                    )
                }
                state.cameraItems
                    .filterNot { item -> state.selected.any { it.uri == item.uri } }
                    .forEach { item -> cameraFileFor(item)?.delete() }
                store.remove(sessionId)
                AlbumPickerResult(items)
            } catch (failure: Throwable) {
                copied.forEach { File(it.filePath).delete() }
                compressedFiles.forEach(File::delete)
                throw failure
            }
        }
    }

    suspend fun cancel(sessionId: String) = withContext(Dispatchers.IO) {
        store.load(sessionId)?.let { state ->
            state.cameraItems.forEach { item -> cameraFileFor(item)?.delete() }
            state.pendingCamera?.let { File(it.filePath).delete() }
        }
        store.remove(sessionId)
    }

    fun registerCamera(
        activity: ComponentActivity,
        sessionId: String,
        onResult: (Result<AlbumPickerSessionSnapshot>) -> Unit,
    ): AlbumCameraLauncher {
        check(activity.lifecycle.currentState != Lifecycle.State.DESTROYED) {
            "相机不能注册到已销毁的 Activity"
        }
        check(!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            "相机必须在 Activity STARTED 前注册"
        }
        lateinit var photoLauncher: androidx.activity.result.ActivityResultLauncher<Uri>
        lateinit var videoLauncher: androidx.activity.result.ActivityResultLauncher<Uri>
        photoLauncher = activity.registerForActivityResult(
            ActivityResultContracts.TakePicture(),
        ) { success ->
            activity.lifecycleScope.launch {
                onResult(completeCamera(sessionId, success))
            }
        }
        videoLauncher = activity.registerForActivityResult(
            ActivityResultContracts.CaptureVideo(),
        ) { success ->
            activity.lifecycleScope.launch {
                onResult(completeCamera(sessionId, success))
            }
        }
        return AlbumCameraLauncher(
            activity = activity,
            client = this,
            sessionId = sessionId,
            photoLauncher = photoLauncher,
            videoLauncher = videoLauncher,
            onResult = onResult,
        )
    }

    private fun requireState(sessionId: String): AlbumPickerSessionState =
        requireNotNull(store.load(sessionId)) { "相册选择会话不存在: $sessionId" }

    private fun lubanDirectory(): File {
        val root = externalRoot()
        return File(root, LUBAN_DIRECTORY).apply {
            if (!exists() && !mkdirs()) throw IOException("无法创建压缩目录")
        }
    }

    private fun fileProviderAuthority(): String =
        "${appContext.packageName}.album.api.fileprovider"

    private fun fileUri(file: File): Uri = try {
        FileProvider.getUriForFile(appContext, fileProviderAuthority(), file)
    } catch (failure: IllegalArgumentException) {
        // Robolectric 不总能合并依赖库的 Provider；正式应用必须使用 content URI。
        val isDebuggable = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (isDebuggable) Uri.fromFile(file) else throw failure
    }

    private fun externalRoot(): File = externalRootOverride
        ?: appContext.getExternalFilesDir(null)
        ?: throw IOException("应用专属外部存储不可用")

    private fun cameraFileFor(item: AlbumPickerSelection): File? = item.filePath?.let(::File)

    private fun AlbumPickerSessionState.toSnapshot() = AlbumPickerSessionSnapshot(
        sessionId = sessionId,
        selectedItems = selected.map { it.toAlbumMedia() },
        cameraItems = cameraItems.map { it.toAlbumMedia() },
        selectedUris = selected.mapTo(linkedSetOf()) { it.uri },
        bucketId = bucketId,
        previewUri = previewUri,
        hasPendingCamera = pendingCamera != null,
    )

    private fun AlbumMedia.toSelection() = AlbumPickerSelection(
        uri = uri,
        mediaType = mediaType,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        durationMillis = durationMillis,
        source = when (source) {
            AlbumMediaSource.MEDIA_STORE -> AlbumPickerItemSource.MEDIA_STORE
            AlbumMediaSource.PHOTO_PICKER -> AlbumPickerItemSource.PHOTO_PICKER
            AlbumMediaSource.CAMERA -> AlbumPickerItemSource.CAMERA
        },
    )

    private fun AlbumPickerSelection.toAlbumMedia() = AlbumMedia(
        uri = uri,
        mediaType = mediaType,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        dateAddedEpochSeconds = null,
        dateModifiedEpochSeconds = null,
        width = width,
        height = height,
        durationMillis = durationMillis,
        bucketId = null,
        bucketName = null,
        selectedAtEpochMillis = null,
        source = when (source) {
            AlbumPickerItemSource.MEDIA_STORE -> AlbumMediaSource.MEDIA_STORE
            AlbumPickerItemSource.PHOTO_PICKER -> AlbumMediaSource.PHOTO_PICKER
            AlbumPickerItemSource.CAMERA -> AlbumMediaSource.CAMERA
        },
    )

    private companion object {
        const val CAMERA_DIRECTORY = "camera"
        const val LUBAN_DIRECTORY = "luban"
    }
}
