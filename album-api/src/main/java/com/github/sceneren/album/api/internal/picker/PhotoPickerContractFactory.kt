package com.github.sceneren.album.api.internal.picker

/** 负责创建 `PhotoPickerContractFactory` 管理的实例。 */
internal object PhotoPickerContractFactory {
    /** 创建或准备 `create` 对应的对象。 */
    fun create(maxSelectionCount: Int?): PickerContract {
        require(maxSelectionCount == null || maxSelectionCount > 0) {
            "maxSelectionCount must be positive when configured"
        }
        return when (maxSelectionCount) {
            null -> PickerContract.MultipleDefault
            1 -> PickerContract.Single
            else -> PickerContract.Multiple(maxSelectionCount)
        }
    }
}
