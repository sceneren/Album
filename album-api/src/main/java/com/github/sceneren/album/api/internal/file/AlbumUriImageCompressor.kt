package com.github.sceneren.album.api.internal.file

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import top.zibin.luban.algorithm.CompressionCalculator
import top.zibin.luban.compression.JpegCompressor
import top.zibin.luban.compression.Compressor
import top.zibin.luban.io.ImageLoader

/** Luban's URI decoding/quality policy, with quota checks before output and atomic publication. */
internal class AlbumUriImageCompressor(private val encoder: Compressor = JpegCompressor()) {
    suspend fun compress(context: Context, uri: Uri, directory: File, beforeWrite: (Long) -> Unit): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("无法读取所选图片")
        input.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("无法解码所选图片")
        val target = CompressionCalculator().calculateTarget(bounds.outWidth, bounds.outHeight)
        var bitmap = ImageLoader().loadFromUri(context, uri, target.width, target.height).bitmap
        val temporary = File(directory, ".${UUID.randomUUID()}.part")
        val output = File(directory, "${UUID.randomUUID()}.jpg")
        try {
            currentCoroutineContext().ensureActive()
            if (bitmap.config != Bitmap.Config.RGB_565 && bitmap.config != Bitmap.Config.ARGB_8888) {
                val converted = requireNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false))
                bitmap.recycle()
                bitmap = converted
            }
            val bytes = encoder.compress(bitmap, target.targetSizeKb, if (target.isLongImage) null else 60)
            currentCoroutineContext().ensureActive()
            if (bytes.isEmpty()) throw IOException("压缩结果为空")
            beforeWrite(bytes.size.toLong())
            FileOutputStream(temporary).use { stream ->
                var offset = 0
                while (offset < bytes.size) {
                    currentCoroutineContext().ensureActive()
                    val count = minOf(64 * 1024, bytes.size - offset)
                    stream.write(bytes, offset, count)
                    offset += count
                }
                stream.fd.sync()
            }
            currentCoroutineContext().ensureActive()
            if (!temporary.renameTo(output)) throw IOException("无法保存压缩图片")
            return output
        } finally {
            temporary.delete()
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}
