package com.github.sceneren.album.api

/** 表示处理并持久化一次 Photo Picker 请求后的结果。 */
sealed interface PhotoPickResult {
    /** 描述 `Selected` 数据。 */
    data class Selected(
        /** 包含的媒体列表。 */
        val media: List<AlbumMedia>,
    ) : PhotoPickResult

    /** 负责 `Cancelled` 相关的数据与行为。 */
    data object Cancelled : PhotoPickResult

    /** 描述 `Failed` 数据。 */
    data class Failed(
        /** 表示 `reason` 对应的数据。 */
        val reason: PhotoPickFailure,
        /** 导致失败的异常。 */
        val cause: Throwable? = null,
    ) : PhotoPickResult
}
