package com.github.sceneren.album.api

/**
 * 相册选择器的公共配置。最大选择数必须在 1 到 100 之间。
 *
 * @property showPermissionUpgrade 外部是否允许显示“申请相册权限”入口；还需宿主 Manifest 声明当前筛选对应的全部权限，
 * 且当前未完全授权。
 */
data class AlbumPickerConfig(
    /** 媒体过滤条件。 */
    val mediaFilter: AlbumMediaFilter,
    /** 允许选择的最大媒体数量。 */
    val maxSelectionCount: Int,
    /** 单选模式下的完成策略。 */
    val singleSelectionFinishMode: SingleSelectionFinishMode =
        SingleSelectionFinishMode.EXPLICIT_CONFIRM,
    /** 相机入口配置。 */
    val camera: AlbumCameraConfig = AlbumCameraConfig(),
    /** 图片压缩配置。 */
    val compression: AlbumCompressionConfig = AlbumCompressionConfig(),
    /** 是否允许显示申请完整媒体权限的入口，最终还会检查宿主 Manifest 与授权状态。 */
    val showPermissionUpgrade: Boolean = true,
) {
    init {
        require(maxSelectionCount in 1..MAX_SELECTION_COUNT) {
            "单次最多只能选择${MAX_SELECTION_COUNT}项，maxSelectionCount 必须在 1..$MAX_SELECTION_COUNT 之间"
        }
    }

    /** 提供类级共享常量与工厂能力。 */
    companion object {
        /** 表示 `MAX_SELECTION_COUNT` 对应的数据。 */
        const val MAX_SELECTION_COUNT: Int = 100
    }
}
