package com.github.sceneren.album.api

import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证 `AlbumCompressionPolicyTest` 覆盖的行为。 */
class AlbumCompressionPolicyTest {
    @Test
    /** 验证 `skipsImagesAtOrBelowConfiguredThresholdAndNeverCompressesVideos` 所描述的场景。 */
    fun skipsImagesAtOrBelowConfiguredThresholdAndNeverCompressesVideos() {
        val policy = AlbumCompressionPolicy(AlbumCompressionConfig(enabled = true, skipAtOrBelowKb = 100))

        assertEquals(
            AlbumCompressionStatus.SKIPPED_SIZE,
            policy.statusFor(AlbumMediaType.IMAGE, 100L * 1024L),
        )
        assertEquals(
            AlbumCompressionStatus.COMPRESSED,
            policy.statusFor(AlbumMediaType.IMAGE, 100L * 1024L + 1L),
        )
        assertEquals(
            AlbumCompressionStatus.NOT_APPLICABLE,
            policy.statusFor(AlbumMediaType.VIDEO, 10_000_000L),
        )
    }
}
