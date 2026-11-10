package com.github.sceneren.photopicker

import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.graphics.Point
import android.os.Bundle
import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.Dimension
import coil3.toAndroidUri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.buffer
import okio.source
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * 通过原始授权 URI 的流读取 Picker 图片，避免平台解码器直接依赖提供方文件描述符。
 * @param data 系统返回且已获授权的 URI，不转换为文件路径或 MediaStore URI。
 * @param options Coil 的请求环境和文件系统。
 */
internal class PhotoPickerImageFetcher(
    private val data: Uri,
    private val options: Options,
    private val preferOriginal: Boolean = false,
) : Fetcher {
    /**
     * 将图片流交给 Coil 管理关闭；不传 ContentMetadata，使用流解码而不主动生成文件副本。
     * @return 保留 MIME 的图片数据源。
     * @throws IOException 提供方无法打开图片时抛出，由 Coil 转为错误状态。
     */
    override suspend fun fetch(): SourceFetchResult {
        currentCoroutineContext().ensureActive()
        val resolver = options.context.contentResolver
        val uri = data.toAndroidUri()
        val mimeType = resolver.getType(uri)
        // Size hints are optional: some providers only support the original content.
        // Keep the request size in Options so Coil still downsamples that original.
        val width = (options.size.width as? Dimension.Pixels)?.px
        val height = (options.size.height as? Dimension.Pixels)?.px
        // Providers may return a cropped thumbnail for EXTRA_SIZE. Preview decoding
        // must read the original asset; Options.size still bounds Coil's bitmap.
        val stream = if (!preferOriginal && width != null && height != null) {
            val extras = Bundle().apply {
                putParcelable(ContentResolver.EXTRA_SIZE, Point(width, height))
            }
            try {
                resolver.openTypedAssetFileDescriptor(uri, "image/*", extras)?.openStream()
            } catch (_: IOException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: UnsupportedOperationException) {
                null
            }
        } else {
            null
        } ?: run {
            currentCoroutineContext().ensureActive()
            resolver.openAssetFileDescriptor(uri, "r")?.openStream()
                ?: throw IOException("无法打开所选图片")
        }

        return try {
            currentCoroutineContext().ensureActive()
            SourceFetchResult(
                source = ImageSource(
                    source = stream.source().buffer(),
                    fileSystem = options.fileSystem,
                ),
                mimeType = mimeType,
                dataSource = DataSource.DISK,
            )
        } catch (failure: Throwable) {
            try {
                stream.close()
            } catch (closeFailure: Throwable) {
                failure.addSuppressed(closeFailure)
            }
            throw failure
        }
    }

    /** Preserve pipe, offset and length semantics; transfer both stream and asset ownership to Coil. */
    private fun AssetFileDescriptor.openStream(): InputStream {
        val stream = try {
            createInputStream()
        } catch (failure: Throwable) {
            try {
                close()
            } catch (closeFailure: Throwable) {
                failure.addSuppressed(closeFailure)
            }
            throw failure
        }
        return object : FilterInputStream(stream) {
            override fun close() {
                this@openStream.use { super.close() }
            }
        }
    }

    /** 只在 Picker 图片请求中注册，其他媒体继续使用宿主加载器。 */
    class Factory(private val preferOriginal: Boolean = false) : Fetcher.Factory<Uri> {
        /**
         * 为内容 URI 创建流读取器。
         * @param data Coil 已规范化的媒体 URI。
         * @param options 请求配置。
         * @param imageLoader 宿主图片加载器。
         * @return 内容 URI 的读取器；其他协议交给后续默认读取器。
         */
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == "content") {
                PhotoPickerImageFetcher(
                    data = data,
                    options = options,
                    preferOriginal = preferOriginal,
                )
            } else {
                null
            }
    }
}
