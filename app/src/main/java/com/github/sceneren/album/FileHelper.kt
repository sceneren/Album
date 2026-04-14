package com.github.sceneren.album

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 文件处理工具类
 *
 * 提供将 content:// URI 转换为真实可用文件路径的能力。
 * 核心策略是将 URI 指向的文件复制到应用内部缓存目录，
 * 返回缓存文件的绝对路径，该路径可直接用于文件上传、压缩等需要真实路径的场景。
 *
 * 缓存文件以 MediaStore ID 作为前缀（如 123_photo.jpg），
 * 确保不同目录下同名文件不会互相覆盖。
 *
 * 这种方式的优势：
 * - 完全兼容 Android 所有版本（包括 Android 10+ 的分区存储）
 * - 不依赖已废弃的 MediaStore DATA 列
 * - 缓存文件位于应用私有目录，无需额外权限即可读写
 * - 应用卸载时缓存自动清理
 *
 * 使用前必须调用 init(context) 进行初始化，建议在 Application.onCreate() 中调用。
 *
 * 使用示例：
 * ```kotlin
 * val filePath = FileHelper.getFileUrl(imageItem.uri)
 * if (filePath != null) {
 *     uploadFile(File(filePath))
 * }
 * ```
 */
object FileHelper {

    // 缓存子目录名称，所有通过本工具复制的文件都存放在此目录下
    private const val CACHE_DIR_NAME = "album_cache"

    // 默认缓冲区大小（8KB），用于流式复制文件
    private const val BUFFER_SIZE = 8192

    // 存储应用级别的 Context，使用 applicationContext 避免内存泄漏
    private lateinit var appContext: Context

    /**
     * 初始化 FileHelper
     *
     * 必须在使用任何方法之前调用，建议在 Application.onCreate() 中调用。
     * 内部会自动提取 applicationContext，传入 Activity 或 Service 也是安全的。
     *
     * 重复调用是安全的，后续调用会覆盖之前的 context。
     *
     * @param context 任意 Context 实例（Activity、Service、Application 均可）
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * 获取应用上下文
     *
     * 若未调用 init() 则抛出异常，提供明确的错误提示。
     *
     * @return 应用级别的 Context
     * @throws IllegalStateException 当 FileHelper 未初始化时抛出
     */
    private fun requireContext(): Context {
        check(::appContext.isInitialized) {
            "FileHelper is not initialized. Call FileHelper.init(context) first."
        }
        return appContext
    }

    /**
     * 将 content:// URI 指向的文件复制到应用内部缓存目录，返回可用的文件路径
     *
     * 通过 ContentResolver.openInputStream 读取 URI 内容，
     * 写入应用缓存目录下的 album_cache 子目录中。
     *
     * 缓存文件名格式为 "{mediaStoreId}_{originalName}"（如 123_photo.jpg），
     * 利用 MediaStore ID 的全局唯一性，避免不同目录下同名文件互相覆盖。
     *
     * 如果对应的缓存文件已存在且大小与源文件一致，会直接返回已有路径，
     * 避免重复复制的 IO 开销。
     *
     * 该方法在 IO 调度器上执行，调用方无需额外切换线程。
     *
     * @param uri content:// 格式的图片 URI
     * @return 缓存文件的绝对路径，复制失败时返回 null
     * @throws IllegalStateException 当 FileHelper 未初始化时抛出
     */
    suspend fun getFileUrl(uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            val context = requireContext()
            val contentResolver = context.contentResolver

            // 构建唯一的缓存文件名：用 MediaStore ID 作为前缀，防止不同目录下同名文件冲突
            val originalName = queryFileName(uri) ?: generateFallbackName(uri, contentResolver)
            val uniqueName = buildUniqueName(uri, originalName)

            // 确保缓存目录存在
            val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val targetFile = File(cacheDir, uniqueName)

            // 如果缓存文件已存在且大小与源文件一致，跳过复制直接返回
            if (targetFile.exists()) {
                val sourceSize = queryFileSize(uri)
                if (sourceSize > 0 && targetFile.length() == sourceSize) {
                    return@withContext targetFile.absolutePath
                }
            }

            // 通过 ContentResolver 打开输入流，将文件内容复制到缓存目录
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(targetFile).use { outputStream ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                        outputStream.flush()
                    }
                }
                targetFile.absolutePath
            } catch (e: Exception) {
                // 复制失败时删除可能不完整的文件，避免后续命中损坏的缓存
                targetFile.delete()
                null
            }
        }
    }

    /**
     * 批量获取多个 URI 的可用文件路径
     *
     * 依次处理每个 URI，返回一个 URI 到缓存路径的映射。
     * 复制失败的 URI 不会出现在结果中。
     *
     * 该方法在 IO 调度器上执行，调用方无需额外切换线程。
     *
     * @param uris 需要处理的 URI 列表
     * @return URI 到缓存文件绝对路径的映射，仅包含成功的条目
     * @throws IllegalStateException 当 FileHelper 未初始化时抛出
     */
    suspend fun getFileUrl(uris: List<Uri>): Map<Uri, String> {
        return withContext(Dispatchers.IO) {
            val result = LinkedHashMap<Uri, String>(uris.size)
            for (uri in uris) {
                val path = getFileUrl(uri)
                if (path != null) {
                    result[uri] = path
                }
            }
            result
        }
    }

    /**
     * 清除全部缓存文件
     *
     * 删除 album_cache 目录下的所有文件。
     * 建议在不再需要上传文件时调用，释放磁盘空间。
     *
     * 该方法在 IO 调度器上执行，调用方无需额外切换线程。
     *
     * @return 成功删除的文件数量
     * @throws IllegalStateException 当 FileHelper 未初始化时抛出
     */
    suspend fun clearCache(): Int {
        return withContext(Dispatchers.IO) {
            val cacheDir = File(requireContext().cacheDir, CACHE_DIR_NAME)
            if (!cacheDir.exists()) {
                return@withContext 0
            }
            var deletedCount = 0
            cacheDir.listFiles()?.forEach { file ->
                if (file.delete()) {
                    deletedCount++
                }
            }
            deletedCount
        }
    }

    /**
     * 获取当前缓存目录占用的磁盘空间大小
     *
     * 遍历 album_cache 目录下所有文件，累加文件大小。
     *
     * @return 缓存目录总大小，单位为字节
     * @throws IllegalStateException 当 FileHelper 未初始化时抛出
     */
    fun getCacheSize(): Long {
        val cacheDir = File(requireContext().cacheDir, CACHE_DIR_NAME)
        if (!cacheDir.exists()) {
            return 0L
        }
        return cacheDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * 构建唯一的缓存文件名
     *
     * 从 content:// URI 中提取 MediaStore ID 作为前缀拼接到原始文件名前。
     * MediaStore ID 在设备上全局唯一，因此即使不同目录下存在同名文件
     * （如 Camera/photo.jpg 和 Screenshots/photo.jpg），
     * 生成的缓存文件名也不同（如 123_photo.jpg 和 456_photo.jpg）。
     *
     * 当 URI 不包含有效 ID 时（非 MediaStore URI），使用 URI 的 hashCode 作为替代前缀。
     *
     * @param uri          content:// 格式的 URI
     * @param originalName 原始文件名（含扩展名）
     * @return 带唯一前缀的文件名
     */
    private fun buildUniqueName(uri: Uri, originalName: String): String {
        // 尝试从 content:// URI 中解析 MediaStore ID
        // 例如 content://media/external/images/media/123 -> id = 123
        val id = try {
            ContentUris.parseId(uri)
        } catch (e: Exception) {
            // 非标准 content:// URI 无法解析 ID，使用 hashCode 替代
            null
        }

        val prefix = id?.toString() ?: uri.hashCode().toUInt().toString()
        return prefix + "_" + originalName
    }

    /**
     * 通过 ContentResolver 查询 URI 对应的原始文件名
     *
     * 查询 OpenableColumns.DISPLAY_NAME 列获取文件名，
     * 这是获取 content:// URI 原始文件名的标准方式。
     *
     * @param uri content:// 格式的 URI
     * @return 文件名（含扩展名），查询失败时返回 null
     */
    private fun queryFileName(uri: Uri): String? {
        val context = requireContext()
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)

        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameCol = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameCol >= 0) {
                        cursor.getString(nameCol)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 通过 ContentResolver 查询 URI 对应的文件大小
     *
     * 查询 OpenableColumns.SIZE 列获取文件字节数，
     * 用于判断缓存文件是否与源文件一致。
     *
     * @param uri content:// 格式的 URI
     * @return 文件大小（字节），查询失败时返回 -1
     */
    private fun queryFileSize(uri: Uri): Long {
        val context = requireContext()
        val projection = arrayOf(OpenableColumns.SIZE)

        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeCol = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeCol >= 0) {
                        cursor.getLong(sizeCol)
                    } else {
                        -1L
                    }
                } else {
                    -1L
                }
            } ?: -1L
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * 当 queryFileName 无法获取原始文件名时，根据 URI 和 MIME 类型生成备用文件名
     *
     * 优先取 URI 的 lastPathSegment，若无效则使用时间戳。
     * 如果文件名缺少扩展名，根据 MIME 类型自动补全。
     *
     * @param uri             content:// 格式的 URI
     * @param contentResolver ContentResolver 用于查询 MIME 类型
     * @return 生成的文件名（含扩展名）
     */
    private fun generateFallbackName(uri: Uri, contentResolver: ContentResolver): String {
        val lastSegment = uri.lastPathSegment

        // 查询 MIME 类型，用于推导扩展名
        val mimeType = contentResolver.getType(uri)
        val extension = if (mimeType != null) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        } else {
            null
        }

        // 如果 lastSegment 包含扩展名，直接使用
        if (lastSegment != null && lastSegment.contains(".")) {
            return lastSegment
        }

        // 用 lastSegment 或时间戳作为基础名，拼上扩展名
        val baseName = lastSegment ?: System.currentTimeMillis().toString()
        val ext = extension ?: "jpg"
        return "$baseName.$ext"
    }
}