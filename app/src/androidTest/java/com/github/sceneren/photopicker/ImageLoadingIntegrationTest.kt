package com.github.sceneren.photopicker

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.video.VideoFrameDecoder
import com.github.sceneren.album.api.AlbumApi
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 在真实设备上验证相册媒体到 Coil 的完整加载链路，用于定位“图片全黑不加载”问题。
 *
 * 断言：当前已授权媒体中至少有一张能被成功解码为包含非黑色像素的位图；
 * 同时对每个失败项输出 URI、来源和错误原因，便于定位是数据层还是渲染层问题。
 */
@RunWith(AndroidJUnit4::class)
class ImageLoadingIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }

    @Test
    fun authorizedMediaCanBeDecodedByCoil() = runBlocking {
        val api = AlbumApi.create(context)
        val status = api.getMediaAccessStatus(AlbumMediaFilter.IMAGES)
        val media = api.loadMediaPage(
            mediaFilter = AlbumMediaFilter.IMAGES,
            offset = 0,
            limit = 100,
        ).getOrThrow()

        assertTrue(
            "当前访问状态 $status 下没有任何可加载媒体（相册为空或未授权）",
            media.isNotEmpty(),
        )

        val images = media.filter { it.mediaType == AlbumMediaType.IMAGE }
        val failures = mutableListOf<String>()
        var nonBlackCount = 0

        android.util.Log.i("AlbumImageTest", "access=$status images=${images.size}")
        images.forEach { item ->
            val outcome = load(item)
            when (outcome) {
                is LoadOutcome.Success -> if (outcome.hasNonBlackPixels) nonBlackCount++
                is LoadOutcome.Failure -> failures += outcome.describe(item)
            }
        }

        assertTrue(
            "访问状态=$status 共 ${images.size} 项，失败 ${failures.size} 项，非黑位图 $nonBlackCount 项：" +
                failures.joinToString(separator = " | "),
            failures.isEmpty() && nonBlackCount == images.size,
        )
    }

    private suspend fun load(media: AlbumMedia): LoadOutcome {
        val request = ImageRequest.Builder(context)
            .data(media.uri)
            .size(256, 256)
            .memoryCacheKey(thumbnailCacheKey(media))
            .scale(coil3.size.Scale.FIT)
            .precision(coil3.size.Precision.INEXACT)
            .fetcherFactory(SystemThumbnailFetcher.Factory())
            .build()
        val initial = imageLoader.execute(request)
        if (initial is SuccessResult) {
            val repeated = imageLoader.execute(request)
            assertTrue("Scrolled-back thumbnail must hit memory cache: ${media.uri}",
                repeated is SuccessResult && repeated.dataSource == coil3.decode.DataSource.MEMORY_CACHE)
        }
        return when (val result = initial) {
            is SuccessResult -> {
                val bitmap = (result.image as? BitmapImage)?.bitmap
                if (bitmap == null) {
                    LoadOutcome.Failure("解码结果不是 BitmapImage")
                } else {
                    LoadOutcome.Success(hasNonBlackPixels = bitmap.hasNonBlackPixel())
                }
            }

            is ErrorResult -> LoadOutcome.Failure(
                "错误=${result.throwable::class.java.name}: ${result.throwable.message}",
            )

        }
    }

    private fun Bitmap.hasNonBlackPixel(): Boolean {
        val software = if (config == Bitmap.Config.HARDWARE) {
            copy(Bitmap.Config.ARGB_8888, false)
        } else {
            this
        }
        try {
            val stepX = (software.width / 16).coerceAtLeast(1)
            val stepY = (software.height / 16).coerceAtLeast(1)
            var y = 0
            while (y < software.height) {
                var x = 0
                while (x < software.width) {
                    if (software.getPixel(x, y) != android.graphics.Color.BLACK) return true
                    x += stepX
                }
                y += stepY
            }
            return false
        } finally {
            if (software !== this) software.recycle()
        }
    }

    private sealed interface LoadOutcome {
        data class Success(val hasNonBlackPixels: Boolean) : LoadOutcome

        data class Failure(val reason: String) : LoadOutcome {
            fun describe(media: AlbumMedia): String =
                "uri=${media.uri} source=${media.source} mime=${media.mimeType} -> $reason"
        }
    }
}
