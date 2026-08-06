package com.github.sceneren.album.api.internal.picker

/** 定义 `PickerContract` 支持的不同状态。 */
internal sealed interface PickerContract {
    /** 负责 `Single` 相关的数据与行为。 */
    data object Single : PickerContract

    /** 负责 `MultipleDefault` 相关的数据与行为。 */
    data object MultipleDefault : PickerContract

    /** 描述 `Multiple` 数据。 */
    data class Multiple(
        /** 表示 `maxItems` 对应的数据。 */
        val maxItems: Int,
    ) : PickerContract
}
