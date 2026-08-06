package com.github.sceneren.album.api

/**
 * 相册选择器的公共配置。最大选择数必须在 1 到 100 之间。
 *
 * @property showPermissionUpgrade 外部是否允许在未完全授权时显示“申请相册权限”入口；完全授权时始终隐藏。
 */
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
