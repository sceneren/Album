package com.github.sceneren.album.api

/** 图片压缩配置；Luban 只会处理图片，视频始终原样返回。 */
data class AlbumCompressionConfig(
    val enabled: Boolean = false,
    val skipAtOrBelowKb: Long = 100L,
) {
    init {
        require(skipAtOrBelowKb >= 0L) { "skipAtOrBelowKb 不能为负数" }
    }
}
