package com.github.sceneren.album.api

import android.content.Intent
import android.net.Uri
import java.util.UUID

/** 两个 UI 模块共享的轻量 Intent 编码，避免通过 Binder 传输大对象。 */
object AlbumPickerIntentCodec {
    const val EXTRA_SESSION_ID = "album_picker.session_id"

    private const val EXTRA_FILTER = "album_picker.filter"
    private const val EXTRA_MAX = "album_picker.max"
    private const val EXTRA_SINGLE_FINISH = "album_picker.single_finish"
    private const val EXTRA_CAMERA_ENABLED = "album_picker.camera_enabled"
    private const val EXTRA_MIXED_CAPTURE = "album_picker.mixed_capture"
    private const val EXTRA_COMPRESSION_ENABLED = "album_picker.compression_enabled"
    private const val EXTRA_SKIP_KB = "album_picker.skip_kb"
    private const val EXTRA_PERMISSION_UPGRADE = "album_picker.permission_upgrade"

    private const val EXTRA_RESULT_ORIGINAL_URIS = "album_picker.result.original_uris"
    private const val EXTRA_RESULT_URIS = "album_picker.result.uris"
    private const val EXTRA_RESULT_TYPES = "album_picker.result.types"
    private const val EXTRA_RESULT_STATUSES = "album_picker.result.statuses"
    private const val EXTRA_RESULT_ORIGINAL_PATHS = "album_picker.result.original_paths"
    private const val EXTRA_RESULT_PATHS = "album_picker.result.paths"

    fun putConfig(intent: Intent, config: AlbumPickerConfig): Intent = intent.apply {
        putExtra(EXTRA_FILTER, config.mediaFilter.name)
        putExtra(EXTRA_MAX, config.maxSelectionCount)
        putExtra(EXTRA_SINGLE_FINISH, config.singleSelectionFinishMode.name)
        putExtra(EXTRA_CAMERA_ENABLED, config.camera.enabled)
        putExtra(EXTRA_MIXED_CAPTURE, config.camera.mixedMediaCaptureType.name)
        putExtra(EXTRA_COMPRESSION_ENABLED, config.compression.enabled)
        putExtra(EXTRA_SKIP_KB, config.compression.skipAtOrBelowKb)
        putExtra(EXTRA_PERMISSION_UPGRADE, config.showPermissionUpgrade)
    }

    fun readConfig(intent: Intent): AlbumPickerConfig = AlbumPickerConfig(
        mediaFilter = AlbumMediaFilter.valueOf(
            requireNotNull(intent.getStringExtra(EXTRA_FILTER)),
        ),
        maxSelectionCount = intent.getIntExtra(EXTRA_MAX, -1),
        singleSelectionFinishMode = SingleSelectionFinishMode.valueOf(
            requireNotNull(intent.getStringExtra(EXTRA_SINGLE_FINISH)),
        ),
        camera = AlbumCameraConfig(
            enabled = intent.getBooleanExtra(EXTRA_CAMERA_ENABLED, true),
            mixedMediaCaptureType = AlbumCameraCaptureType.valueOf(
                requireNotNull(intent.getStringExtra(EXTRA_MIXED_CAPTURE)),
            ),
        ),
        compression = AlbumCompressionConfig(
            enabled = intent.getBooleanExtra(EXTRA_COMPRESSION_ENABLED, false),
            skipAtOrBelowKb = intent.getLongExtra(EXTRA_SKIP_KB, 100L),
        ),
        showPermissionUpgrade = intent.getBooleanExtra(EXTRA_PERMISSION_UPGRADE, true),
    )

    fun newSessionId(): String = UUID.randomUUID().toString()

    fun putResult(intent: Intent, result: AlbumPickerResult): Intent = intent.apply {
        putExtra(EXTRA_RESULT_ORIGINAL_URIS, ArrayList(result.items.map { it.originalUri.toString() }))
        putExtra(EXTRA_RESULT_URIS, ArrayList(result.items.map { it.resultUri.toString() }))
        putExtra(EXTRA_RESULT_TYPES, result.items.map { it.mediaType.name }.toTypedArray())
        putExtra(EXTRA_RESULT_STATUSES, result.items.map { it.compressionStatus.name }.toTypedArray())
        putExtra(EXTRA_RESULT_ORIGINAL_PATHS, result.items.map { it.originalFilePath }.toTypedArray())
        putExtra(EXTRA_RESULT_PATHS, result.items.map { it.filePath }.toTypedArray())
    }

    fun readResult(intent: Intent): AlbumPickerResult {
        val originals = requireNotNull(intent.getStringArrayListExtra(EXTRA_RESULT_ORIGINAL_URIS))
        val resultUris = requireNotNull(intent.getStringArrayListExtra(EXTRA_RESULT_URIS))
        val types = requireNotNull(intent.getStringArrayExtra(EXTRA_RESULT_TYPES))
        val statuses = requireNotNull(intent.getStringArrayExtra(EXTRA_RESULT_STATUSES))
        val originalPaths = requireNotNull(intent.getStringArrayExtra(EXTRA_RESULT_ORIGINAL_PATHS))
        val paths = requireNotNull(intent.getStringArrayExtra(EXTRA_RESULT_PATHS))
        require(
            originals.size == resultUris.size && originals.size == types.size &&
                originals.size == statuses.size && originals.size == originalPaths.size &&
                originals.size == paths.size,
        ) { "相册结果字段数量不一致" }
        return AlbumPickerResult(
            items = originals.indices.map { index ->
                AlbumPickerResultItem(
                    originalUri = Uri.parse(originals[index]),
                    resultUri = Uri.parse(resultUris[index]),
                    mediaType = AlbumMediaType.valueOf(types[index]),
                    compressionStatus = AlbumCompressionStatus.valueOf(statuses[index]),
                    originalFilePath = originalPaths[index],
                    filePath = paths[index],
                )
            },
        )
    }
}
