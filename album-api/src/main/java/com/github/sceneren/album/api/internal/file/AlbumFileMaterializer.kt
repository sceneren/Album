package com.github.sceneren.album.api.internal.file

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 将用户确认的 content URI 流式复制为应用专属文件。 */
internal class AlbumFileMaterializer(
    context: Context,
    private val resolver: ContentResolver,
    private val externalRoot: File? = context.applicationContext.getExternalFilesDir(null),
) {
    suspend fun copy(uri: Uri, displayName: String?): MaterializedMedia = withContext(Dispatchers.IO) {
        copyOnIo(uri, displayName)
    }

    suspend fun copyAll(items: List<Pair<Uri, String?>>): List<MaterializedMedia> =
        withContext(Dispatchers.IO) {
            val copied = mutableListOf<MaterializedMedia>()
            try {
                items.forEach { (uri, displayName) ->
                    copied += copyOnIo(uri, displayName)
                }
                copied
            } catch (failure: Throwable) {
                copied.forEach { media -> File(media.filePath).delete() }
                throw failure
            }
        }

    private fun copyOnIo(uri: Uri, displayName: String?): MaterializedMedia {
        val root = externalRoot ?: throw IOException("应用专属外部存储不可用")
        val directory = File(root, PHOTO_PICKER_DIRECTORY).apply {
            if (!exists() && !mkdirs()) throw IOException("无法创建 $absolutePath")
        }
        val target = File(directory, uniqueFileName(displayName))
        val temporary = File(directory, ".${target.name}.part")
        return try {
            openInput(uri).use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output, BUFFER_SIZE)
                    output.fd.sync()
                }
            }
            if (!temporary.renameTo(target)) {
                throw IOException("无法提交文件 ${target.absolutePath}")
            }
            MaterializedMedia(
                originalUri = uri,
                originalFilePath = target.absolutePath,
                filePath = target.absolutePath,
                sizeBytes = target.length(),
            )
        } catch (failure: Throwable) {
            temporary.delete()
            target.delete()
            throw failure
        }
    }

    private fun openInput(uri: Uri) = resolver.openInputStream(uri)
        ?: throw IOException("无法读取媒体 URI: $uri")

    private fun uniqueFileName(displayName: String?): String {
        val safeName = displayName
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.take(80)
            ?.takeIf { it.isNotBlank() }
            ?: "media"
        return "${UUID.randomUUID()}_$safeName"
    }

    companion object {
        const val PHOTO_PICKER_DIRECTORY: String = "photo_picker"
        private const val BUFFER_SIZE = 64 * 1024
    }
}

internal data class MaterializedMedia(
    val originalUri: Uri,
    val originalFilePath: String,
    val filePath: String,
    val sizeBytes: Long,
)
