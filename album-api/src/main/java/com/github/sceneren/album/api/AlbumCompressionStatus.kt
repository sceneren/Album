package com.github.sceneren.album.api

/** 表示单个结果项应用图片压缩策略后的状态。 */
enum class AlbumCompressionStatus {
    DISABLED,
    SKIPPED_SIZE,
    COMPRESSED,
    NOT_APPLICABLE,
}
