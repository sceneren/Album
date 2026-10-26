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
        val stream = resolver.openInputStream(uri) ?: throw IOException("无法打开所选图片")
        return SourceFetchResult(
            source = ImageSource(
                source = stream.source().buffer(),
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
