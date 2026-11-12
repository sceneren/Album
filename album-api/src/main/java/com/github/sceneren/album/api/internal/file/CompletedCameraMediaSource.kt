package com.github.sceneren.album.api.internal.file

import android.content.Context
import android.net.Uri
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.api.AlbumPickerClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Enumerate cheap file attributes first; probe only camera files actually returned by a page. */
internal class CompletedCameraMediaSource(
    private val context: Context,
    private val externalRoot: File? = null,
    private val probe: (File, AlbumMediaType) -> AlbumPickerClient.ProbedCameraMetadata =
        { file, type -> AlbumPickerClient.probeCameraFileMetadata(file, type) },
) {
    private val files = ConcurrentHashMap<Uri, File>()
    private data class Version(val path: String, val size: Long, val modified: Long)
    private val metadata = object : LinkedHashMap<Version, AlbumPickerClient.ProbedCameraMetadata>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Version, AlbumPickerClient.ProbedCameraMetadata>?) = size > 128
    }

    fun list(filter: AlbumMediaFilter): List<AlbumMedia> {
        val current = mutableMapOf<Uri, File>()
        val result = AlbumPickerClient.loadCompletedCameraMedia(
            context, filter, externalRoot,
            onFile = { uri, file -> current[uri] = file },
        )
        files.putAll(current)
        files.entries.removeAll { !it.value.isFile }
        return result
    }

    fun readMetadata(item: AlbumMedia): AlbumMedia {
        val file = files[item.uri] ?: return item
        if (!file.isFile) return item
        val version = Version(file.absolutePath, file.length(), file.lastModified())
        val cached = synchronized(metadata) { metadata[version] }
        val value = cached ?: probe(file, item.mediaType).also {
            synchronized(metadata) { metadata[version] = it }
        }
        return item.copy(width = value.width, height = value.height, durationMillis = value.durationMillis)
    }
}
