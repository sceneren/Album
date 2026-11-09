package com.github.sceneren.album.api

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.github.sceneren.album.api.internal.file.AlbumFileMaterializer
import com.github.sceneren.album.api.internal.file.MaterializationRequest
import com.github.sceneren.album.api.internal.file.MaterializedMedia
import com.github.sceneren.album.api.internal.picker.allows
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
import java.util.Locale
import java.util.UUID

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

    /** 仅更新选择状态，不读取或复制媒体内容；文件复制延迟到 [confirm]。 */
    suspend fun toggleSelection(
        sessionId: String,
        media: AlbumMedia,
    ): Result<AlbumPickerSessionSnapshot> = runCatching {
        withContext(Dispatchers.IO) {
            val state = requireState(sessionId)
            val existingCameraPath = state.cameraItems
                .firstOrNull { it.uri == media.uri }
                ?.filePath
            val item = media.toSelection().copy(filePath = existingCameraPath)
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

            val dimensionsAndDuration = probeCameraFileMetadata(file, pending.mediaType)
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
                width = dimensionsAndDuration.width,
                height = dimensionsAndDuration.height,
                durationMillis = dimensionsAndDuration.durationMillis,
                specialFormat = AlbumMediaSpecialFormat.NONE,
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
            next.toSnapshot()
        }
    }

    /** 用户确认选择后，仅复制最终选中项并按配置压缩；相机已生成的私有文件直接复用不重复复制。 */
    suspend fun confirm(sessionId: String): Result<AlbumPickerResult> = runCatching {
        withContext(Dispatchers.IO) {
            val state = requireState(sessionId)
            check(state.pendingCamera == null) { "拍摄尚未完成" }
            check(state.selected.isNotEmpty()) { "至少选择一个媒体" }
            val copied = state.selected.map { selection ->
                materializeSelection(selection)
            }
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

    /** 取消本次选择会话；已完成拍摄保留在相机目录中，仅删除尚未完成的拍摄文件。 */
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

    /** 物化单个选中项：若已是本应用相机目录中的有效文件则直接复用，避免二次复制。 */
    private suspend fun materializeSelection(
        selection: AlbumPickerSelection,
    ): MaterializedMedia {
        val maxBytes = if (selection.mediaType == AlbumMediaType.IMAGE) MAX_IMAGE_BYTES else null
        if (selection.source == AlbumPickerItemSource.CAMERA && !selection.filePath.isNullOrBlank()) {
            val cameraFile = File(selection.filePath)
            if (cameraFile.isFile && cameraFile.length() > 0L) {
                if (maxBytes != null && cameraFile.length() > maxBytes) {
                    throw IOException("Selected media exceeds the allowed size")
                }
                return MaterializedMedia(
                    originalUri = selection.uri,
                    originalFilePath = cameraFile.absolutePath,
                    filePath = cameraFile.absolutePath,
                    sizeBytes = cameraFile.length(),
                    reused = true,
                )
            }
        }
        return materializer.copy(
            MaterializationRequest(
                uri = selection.uri,
                displayName = selection.displayName,
                mimeType = selection.mimeType,
                sizeBytes = selection.sizeBytes,
                dateModifiedEpochSeconds = selection.dateModifiedEpochSeconds,
                maxBytes = maxBytes,
            ),
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

    /** 执行 `fileUri` 方法定义的处理。 */
    private fun fileUri(file: File): Uri = resolveFileUri(appContext, file)

    /** 执行 `externalRoot` 方法定义的处理。 */
    private fun externalRoot(): File = externalRootOverride
        ?: appContext.getExternalFilesDir(null)
        ?: throw IOException("应用专属外部存储不可用")

    private data class ProbedCameraMetadata(
        val width: Int? = null,
        val height: Int? = null,
        val durationMillis: Long? = null,
    )

    /** 提供类级共享常量与工厂能力。 */
    internal companion object {
        /** 表示 `CAMERA_DIRECTORY` 对应的数据。 */
        const val CAMERA_DIRECTORY = "camera"
        /** 表示 `LUBAN_DIRECTORY` 对应的数据。 */
        const val LUBAN_DIRECTORY = "luban"
        /** 单张图片复制上限，避免异常提供方耗尽应用存储。 */
        const val MAX_IMAGE_BYTES = 256L * 1024 * 1024
        const val MILLIS_PER_SECOND = 1_000L

        /** 读取应用专属相机目录中已完成的拍摄媒体，供无完整媒体权限时跨会话展示。 */
        fun loadCompletedCameraMedia(
            context: Context,
            mediaFilter: AlbumMediaFilter,
            externalRootOverride: File? = null,
        ): List<AlbumMedia> {
            val appContext = context.applicationContext
            val root = externalRootOverride ?: appContext.getExternalFilesDir(null) ?: return emptyList()
            val cameraDir = File(root, CAMERA_DIRECTORY)
            if (!cameraDir.isDirectory) return emptyList()
            val pendingPaths = AlbumPickerSessionStore(appContext).pendingCameraFilePaths()
            val files = cameraDir.listFiles()
                ?.filter { file ->
                    file.isFile && file.length() > 0L && file.absolutePath !in pendingPaths
                }
                ?.sortedWith(
                    compareByDescending<File> { it.lastModified() }
                        .thenBy { it.name },
                )
                ?: return emptyList()

            return files.mapNotNull { file ->
                val lowerName = file.name.lowercase(Locale.ROOT)
                val mediaType = when {
                    lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") -> AlbumMediaType.IMAGE
                    lowerName.endsWith(".mp4") -> AlbumMediaType.VIDEO
                    else -> return@mapNotNull null
                }
                if (!mediaFilter.allows(mediaType)) return@mapNotNull null
                val metadata = probeCameraFileMetadata(file, mediaType)
                val lastModifiedMillis = file.lastModified().takeIf { it > 0L }
                AlbumMedia(
                    uri = resolveFileUri(appContext, file),
                    mediaType = mediaType,
                    displayName = file.name,
                    mimeType = if (mediaType == AlbumMediaType.IMAGE) "image/jpeg" else "video/mp4",
                    sizeBytes = file.length(),
                    dateAddedEpochSeconds = lastModifiedMillis?.div(MILLIS_PER_SECOND),
                    dateModifiedEpochSeconds = lastModifiedMillis?.div(MILLIS_PER_SECOND),
                    width = metadata.width,
                    height = metadata.height,
                    durationMillis = metadata.durationMillis,
                    bucketId = null,
                    bucketName = null,
                    selectedAtEpochMillis = lastModifiedMillis,
                    source = AlbumMediaSource.CAMERA,
                    specialFormat = AlbumMediaSpecialFormat.NONE,
                )
            }
        }

        /** 若 [uri] 指向相机目录中的已完成文件则删除并返回 true。 */
        fun deleteCameraMediaByUri(
            context: Context,
            uri: Uri,
            externalRootOverride: File? = null,
        ): Boolean {
            val appContext = context.applicationContext
            val root = externalRootOverride ?: appContext.getExternalFilesDir(null) ?: return false
            val cameraDir = File(root, CAMERA_DIRECTORY)
            if (!cameraDir.isDirectory) return false
            val target = cameraDir.listFiles()
                ?.firstOrNull { file -> file.isFile && resolveFileUri(appContext, file) == uri }
                ?: return false
            return target.delete()
        }

        private fun resolveFileUri(appContext: Context, file: File): Uri = try {
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.album.api.fileprovider",
                file,
            )
        } catch (failure: IllegalArgumentException) {
            // Robolectric 不总能合并依赖库的 Provider；正式应用必须使用 content URI。
            val isDebuggable = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            if (isDebuggable) Uri.fromFile(file) else throw failure
        }

        private fun probeCameraFileMetadata(
            file: File,
            mediaType: AlbumMediaType,
        ): ProbedCameraMetadata = runCatching {
            if (mediaType == AlbumMediaType.IMAGE) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                ProbedCameraMetadata(
                    width = options.outWidth.takeIf { it > 0 },
                    height = options.outHeight.takeIf { it > 0 },
                )
            } else {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    val width = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull()
                        ?.takeIf { it > 0 }
                    val height = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull()
                        ?.takeIf { it > 0 }
                    val duration = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                        ?.takeIf { it > 0L }
                    ProbedCameraMetadata(width = width, height = height, durationMillis = duration)
                } finally {
                    retriever.release()
                }
            }
        }.getOrDefault(ProbedCameraMetadata())
    }
}
