package com.github.sceneren.album.api.internal.file

import android.content.Context
import android.annotation.SuppressLint
import android.os.StatFs
import androidx.core.content.edit
import com.github.sceneren.album.api.AlbumPickerFileTooLargeException
import com.github.sceneren.album.api.AlbumPickerResult
import com.github.sceneren.album.api.AlbumPickerResultItem
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray

/** Bounded generated-file storage. Completed camera captures are originals, never LRU victims. */
internal class AlbumGeneratedMediaCache(
    context: Context,
    private val root: File,
    private val availableBytes: () -> Long = {
        var existing: File = root
        while (!existing.exists() && existing.parentFile != null) existing = existing.parentFile!!
        // Budget actual free bytes; allocatable/reclaimable bytes could include other cache data.
        StatFs(existing.absolutePath).availableBytes
    },
) {
    private val preferences by lazy {
        context.applicationContext.getSharedPreferences("album_generated_media_leases", Context.MODE_PRIVATE)
    }
    private val managedRoots by lazy { listOf("photo_picker", "luban").map { File(root, it).canonicalFile } }

    private fun managed(file: File): Boolean {
        val path = file.canonicalPath
        return managedRoots.any { path.startsWith(it.path + File.separator) }
    }

    suspend fun <T> withBudget(block: suspend (Budget) -> T): T = mutex.withLock {
        val pinned = preferences.all.values.flatMap { value ->
            val paths = runCatching { JSONArray(value as String) }.getOrNull()
            if (paths == null) emptyList() else List(paths.length()) { paths.getString(it) }
        }.toSet()
        block(Budget((availableBytes() / 2L).coerceAtLeast(0L), pinned))
    }

    inner class Budget internal constructor(val capacityBytes: Long, pinned: Set<String>) {
        private val protectedPaths = pinned.toMutableSet()
        private val files = managedRoots.asSequence().flatMap { it.walkTopDown() }
            .filter { it.isFile && managed(it) }.toList()
        private var usedBytes = files.sumOf { it.length() }
        private val candidates = java.util.ArrayDeque(files.sortedBy { it.lastModified() })

        fun checkFileSize(size: Long) {
            if (size > capacityBytes) throw AlbumPickerFileTooLargeException()
        }

        /** Called before each bounded copy buffer; no directory scan is performed per buffer. */
        fun reserve(bytes: Long) {
            require(bytes >= 0L)
            while (bytes > capacityBytes - usedBytes) {
                var evicted = false
                while (candidates.isNotEmpty()) {
                    val oldest = candidates.removeFirst()
                    if (oldest.canonicalPath in protectedPaths) continue
                    val length = oldest.length()
                    if (oldest.delete()) {
                        usedBytes -= length
                        evicted = true
                        break
                    }
                }
                if (!evicted) throw IOException("可用存储空间不足，请先释放不再使用的相册结果")
            }
            usedBytes += bytes
        }

        fun protect(path: String) {
            val file = File(path)
            if (managed(file)) {
                protectedPaths += file.canonicalPath
                file.setLastModified(System.currentTimeMillis())
            }
        }

        // KTX edit(commit=true) discards commit's success flag, needed for durable publication.
        @SuppressLint("UseKtx")
        fun publish(items: List<AlbumPickerResultItem>, compressedFiles: List<File>): AlbumPickerResult {
            // A reused cache may itself exceed a reduced budget after other apps consume storage.
            reserve(0)
            val paths = compressedFiles.filter(::managed).map { it.canonicalPath }.distinct()
            if (paths.isEmpty()) return AlbumPickerResult(items)
            val lease = UUID.randomUUID().toString()
            // Publication runs on IO. Make the protection durable before returning a usable URI.
            if (!preferences.edit().putString(lease, JSONArray(paths).toString()).commit()) {
                preferences.edit { remove(lease) }
                throw IOException("无法保存压缩结果状态")
            }
            return AlbumPickerResult(items, generatedMediaLeaseId = lease)
        }
    }

    /** Releasing twice is safe. Other results sharing the same file retain their own leases. */
    suspend fun release(result: AlbumPickerResult) = mutex.withLock {
        result.generatedMediaLeaseId?.let { id ->
            val paths = preferences.getString(id, null)?.let { runCatching { JSONArray(it) }.getOrNull() }
            if (paths != null) repeat(paths.length()) { index ->
                val file = File(paths.getString(index))
                if (managed(file) && file.isFile) file.setLastModified(System.currentTimeMillis())
            }
            preferences.edit { remove(id) }
        }
    }

    suspend fun <T> exclusive(block: () -> T): T = mutex.withLock { block() }

    fun forgetAllLeases() { preferences.edit { clear() } }

    private companion object {
        // Includes copying/compressing and explicit cleanup, across all client instances.
        val mutex = Mutex()
    }
}
