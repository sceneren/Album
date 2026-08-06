package com.github.sceneren.album.api

/** 根据媒体类型和大小决定是否进入图片压缩流程。 */
internal class AlbumCompressionPolicy(
    private val config: AlbumCompressionConfig,
) {
    /** 执行 `statusFor` 方法定义的处理。 */
    fun statusFor(mediaType: AlbumMediaType, sizeBytes: Long?): AlbumCompressionStatus {
        if (mediaType == AlbumMediaType.VIDEO) return AlbumCompressionStatus.NOT_APPLICABLE
        if (!config.enabled) return AlbumCompressionStatus.DISABLED
        val thresholdBytes = config.skipAtOrBelowKb
            .coerceAtMost(Long.MAX_VALUE / 1024L)
            .times(1024L)
        return if (sizeBytes != null && sizeBytes <= thresholdBytes) {
            AlbumCompressionStatus.SKIPPED_SIZE
        } else {
            AlbumCompressionStatus.COMPRESSED
        }
    }
}
