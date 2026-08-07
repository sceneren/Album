package com.github.sceneren.album.api

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.github.sceneren.album.api.internal.database.PickedMediaDraft
import com.github.sceneren.album.api.internal.database.PickedMediaStore
import com.github.sceneren.album.api.internal.file.AlbumFileMaterializer
import com.github.sceneren.album.api.internal.file.MaterializationRequest
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

/** 相册 UI 使用的会话、相机和最终文件处理器。 */
class AlbumPickerClient internal constructor(
    private val context: Context,
    private val externalRootOverride: File? = null,
    private val pickedStore: PickedMediaStore? = null,
) {
    private val appContext = context.applicationContext
    private val store = AlbumPickerSessionStore(appContext)
    private val materializer = AlbumFileMaterializer(
        appContext,
        appContext.contentResolver,
        externalRootOverride ?: appContext.getExternalFilesDir(null),
    )
    /** 执行 `openSession` 方法定义的处理。 */
    fun openSession(config: AlbumPickerConfig, sessionId: String? = null): AlbumPickerSessionSnapshot {
        val state = sessionId?.let(store::load)
            ?.takeIf { it.config == config }
            ?: store.create(config, sessionId ?: UUID.randomUUID().toString())
        return state.toSnapshot()
    }

    /** 执行 `snapshot` 方法定义的处理。 */
    fun snapshot(sessionId: String): AlbumPickerSessionSnapshot =
        requireNotNull(store.load(sessionId)) { "相册选择会话不存在: $sessionId" }.toSnapshot()

    /** 更新 `toggleSelection` 对应的状态。 */
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

    /** 更新 `setBucket` 对应的状态。 */
    suspend fun setBucket(sessionId: String, bucketId: Long): Result<AlbumPickerSessionSnapshot> =
        runCatching {
            withContext(Dispatchers.IO) {
                val state = requireState(sessionId).copy(bucketId = bucketId)
                store.save(state)
                state.toSnapshot()
            }
        }

    /** 创建或准备 `prepareCamera` 对应的对象。 */
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

    /** 执行 `completeCamera` 方法定义的处理。 */
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
                dateModifiedEpochSeconds = file.lastModified()
                    .takeIf { it > 0L }
                    ?.div(MILLIS_PER_SECOND),
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
            pickedStore?.upsertBatch(
                listOf(
                    PickedMediaDraft(
                        uri = item.uri.toString(),
                        mediaType = item.mediaType.name,
                        displayName = item.displayName,
                        mimeType = item.mimeType,
                        sizeBytes = item.sizeBytes,
                        width = item.width,
                        height = item.height,
                        durationMillis = item.durationMillis,
                        selectedAtEpochMillis = System.currentTimeMillis(),
                        ownsPersistableGrant = false,
                    ),
                ),
            )
            next.toSnapshot()
        }
    }

    /** 执行 `confirm` 方法定义的处理。 */
    suspend fun confirm(sessionId: String): Result<AlbumPickerResult> = runCatching {
        withContext(Dispatchers.IO) {
            val state = requireState(sessionId)
            check(state.pendingCamera == null) { "拍摄尚未完成" }
            check(state.selected.isNotEmpty()) { "至少选择一个媒体" }
            val copied = materializer.copyAll(
                state.selected.map { selection ->
                    MaterializationRequest(
                        uri = selection.uri,
                        displayName = selection.displayName,
                        mimeType = selection.mimeType,
                        sizeBytes = selection.sizeBytes,
                        dateModifiedEpochSeconds = selection.dateModifiedEpochSeconds,
                    )
                },
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
                store.remove(sessionId)
                AlbumPickerResult(items)
            } catch (failure: Throwable) {
                compressedFiles.forEach(File::delete)
                throw failure
            }
        }
    }

    /** 取消本次选择会话；已完成拍摄保留在持久列表中，仅删除尚未完成的拍摄文件。 */
    suspend fun cancel(sessionId: String) = withContext(Dispatchers.IO) {
        store.load(sessionId)?.let { state ->
            state.pendingCamera?.let { File(it.filePath).delete() }
        }
        store.remove(sessionId)
    }

    /** 创建或准备 `registerCamera` 对应的对象。 */
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

    /** 执行 `requireState` 方法定义的处理。 */
    private fun requireState(sessionId: String): AlbumPickerSessionState =
        requireNotNull(store.load(sessionId)) { "相册选择会话不存在: $sessionId" }

    /** 执行 `lubanDirectory` 方法定义的处理。 */
    private fun lubanDirectory(): File {
        val root = externalRoot()
        return File(root, LUBAN_DIRECTORY).apply {
            if (!exists() && !mkdirs()) throw IOException("无法创建压缩目录")
        }
    }

    /** 执行 `fileProviderAuthority` 方法定义的处理。 */
    private fun fileProviderAuthority(): String =
        "${appContext.packageName}.album.api.fileprovider"

    /** 执行 `fileUri` 方法定义的处理。 */
    private fun fileUri(file: File): Uri = try {
        FileProvider.getUriForFile(appContext, fileProviderAuthority(), file)
    } catch (failure: IllegalArgumentException) {
        // Robolectric 不总能合并依赖库的 Provider；正式应用必须使用 content URI。
        val isDebuggable = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (isDebuggable) Uri.fromFile(file) else throw failure
    }

    /** 执行 `externalRoot` 方法定义的处理。 */
    private fun externalRoot(): File = externalRootOverride
        ?: appContext.getExternalFilesDir(null)
        ?: throw IOException("应用专属外部存储不可用")

    /** 提供类级共享常量与工厂能力。 */
    private companion object {
        /** 表示 `CAMERA_DIRECTORY` 对应的数据。 */
        const val CAMERA_DIRECTORY = "camera"
        /** 表示 `LUBAN_DIRECTORY` 对应的数据。 */
        const val LUBAN_DIRECTORY = "luban"
        const val MILLIS_PER_SECOND = 1_000L
    }
}
