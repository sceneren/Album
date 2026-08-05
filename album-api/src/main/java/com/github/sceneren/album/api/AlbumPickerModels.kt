package com.github.sceneren.album.api

import android.net.Uri

/** 相册选择器的公共配置。最大选择数必须在 1 到 100 之间。 */
data class AlbumPickerConfig(
    val mediaFilter: AlbumMediaFilter,
    val maxSelectionCount: Int,
    val singleSelectionFinishMode: SingleSelectionFinishMode =
        SingleSelectionFinishMode.EXPLICIT_CONFIRM,
    val camera: AlbumCameraConfig = AlbumCameraConfig(),
    val compression: AlbumCompressionConfig = AlbumCompressionConfig(),
    val showPermissionUpgrade: Boolean = true,
) {
    init {
        require(maxSelectionCount in 1..MAX_SELECTION_COUNT) {
            "单次最多只能选择${MAX_SELECTION_COUNT}项，maxSelectionCount 必须在 1..$MAX_SELECTION_COUNT 之间"
        }
    }

    companion object {
        const val MAX_SELECTION_COUNT: Int = 100
    }
}

/** 单选时点击媒体后的完成策略。 */
enum class SingleSelectionFinishMode {
    EXPLICIT_CONFIRM,
    IMMEDIATE,
}

/** 相机入口和混合媒体模式下的拍摄类型。 */
data class AlbumCameraConfig(
    val enabled: Boolean = true,
    val mixedMediaCaptureType: AlbumCameraCaptureType = AlbumCameraCaptureType.PHOTO,
)

enum class AlbumCameraCaptureType {
    PHOTO,
    VIDEO,
}

/** 图片压缩配置；Luban 只会处理图片，视频始终原样返回。 */
data class AlbumCompressionConfig(
    val enabled: Boolean = false,
    val skipAtOrBelowKb: Long = 100L,
) {
    init {
        require(skipAtOrBelowKb >= 0L) { "skipAtOrBelowKb 不能为负数" }
    }
}

/** 相册页完成后返回的媒体集合，顺序与用户选择顺序一致。 */
data class AlbumPickerResult(
    val items: List<AlbumPickerResultItem>,
)

/** 一个选择结果。resultUri/filePath 是宿主实际应消费的结果。 */
data class AlbumPickerResultItem(
    val originalUri: Uri,
    val resultUri: Uri,
    val mediaType: AlbumMediaType,
    val compressionStatus: AlbumCompressionStatus,
    /** 复制到应用专属 files/photo_picker 后的原始文件绝对路径。 */
    val originalFilePath: String,
    /** 最终返回文件绝对路径；未压缩时与 originalFilePath 相同。 */
    val filePath: String,
)

enum class AlbumCompressionStatus {
    DISABLED,
    SKIPPED_SIZE,
    COMPRESSED,
    NOT_APPLICABLE,
}
