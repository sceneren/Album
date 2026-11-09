package com.github.sceneren.photopicker

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.toAndroidUri
import okio.buffer
import okio.source
import java.io.IOException

/**
 * 通过原始授权 URI 的流读取 Picker 图片，避免平台解码器直接依赖提供方文件描述符。
 * @param data 系统返回且已获授权的 URI，不转换为文件路径或 MediaStore URI。
 * @param options Coil 的请求环境和文件系统。
 */
internal class PhotoPickerImageFetcher(
    private val data: Uri,
    private val options: Options,
) : Fetcher {
    /**
     * 将图片流交给 Coil 管理关闭；不传 ContentMetadata，使用流解码而不主动生成文件副本。
     * @return 保留 MIME 的图片数据源。
     * @throws IOException 提供方无法打开图片时抛出，由 Coil 转为错误状态。
     */
    override suspend fun fetch(): SourceFetchResult {
        val resolver = options.context.contentResolver
        val uri = data.toAndroidUri()
        val mimeType = resolver.getType(uri)
        // Picker providers can return a pipe or a non-zero-offset asset. Preserve the
        // AssetFileDescriptor stream semantics and ask the provider for the requested size
        // instead of opening many full-resolution originals concurrently.
        val width = (options.size.width as? coil3.size.Dimension.Pixels)?.px
        val height = (options.size.height as? coil3.size.Dimension.Pixels)?.px
        val extras = android.os.Bundle().apply {
            if (width != null && height != null) {
                putParcelable(android.content.ContentResolver.EXTRA_SIZE, android.graphics.Point(width, height))
            }
        }
        val asset = resolver.openTypedAssetFileDescriptor(uri, "image/*", extras)
            ?: throw IOException("无法打开所选图片")
        val stream = try {
            asset.createInputStream()
        } catch (failure: Throwable) {
            asset.close()
            throw failure
        }
        return SourceFetchResult(
            source = ImageSource(
                source = object : java.io.FilterInputStream(stream) {
                    override fun close() {
                        try {
                            super.close()
                        } finally {
                            asset.close()
                        }
                    }
                }.source().buffer(),
                fileSystem = options.fileSystem,
            ),
            mimeType = mimeType,
            dataSource = DataSource.DISK,
        )
    }

    /** 只在 Picker 图片请求中注册，其他媒体继续使用宿主加载器。 */
    class Factory : Fetcher.Factory<Uri> {
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
                )
            } else {
                null
            }
    }
}
