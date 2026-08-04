package com.github.sceneren.album.api.internal.mediastore

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AndroidMediaStoreDataSource(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MediaStoreDataSource {
    private val contentResolver = context.applicationContext.contentResolver
    private val filesUri = MediaStore.Files.getContentUri(EXTERNAL_VOLUME)

    override suspend fun loadPage(
        mediaFilter: AlbumMediaFilter,
        bucketId: Long,
        offset: Int,
        limit: Int,
    ): List<AlbumMedia> = withContext(ioDispatcher) {
        require(offset >= 0) { "offset must not be negative" }
        require(limit > 0) { "limit must be positive" }

        val spec = MediaStoreQuerySpec.create(mediaFilter, bucketId)
        query(spec, limit = limit, offset = offset)
    }

    override suspend fun getDirectories(
        mediaFilter: AlbumMediaFilter,
    ): List<AlbumDirectory> = withContext(ioDispatcher) {
        val media = query(
            spec = MediaStoreQuerySpec.create(mediaFilter, AlbumDirectory.ALL_BUCKET_ID),
            limit = null,
            offset = null,
        )
        if (media.isEmpty()) return@withContext emptyList()

        val realDirectories = LinkedHashMap<Long, MutableDirectory>()
        media.forEach { item ->
            val bucketId = item.bucketId ?: return@forEach
            val directory = realDirectories.getOrPut(bucketId) {
                MutableDirectory(
                    bucketId = bucketId,
                    bucketName = item.bucketName,
                    coverUri = item.uri,
                    coverMediaType = item.mediaType,
                    firstMediaDate = item.dateAddedEpochSeconds ?: Long.MIN_VALUE,
                )
            }
            directory.mediaCount++
        }

        val all = AlbumDirectory(
            bucketId = AlbumDirectory.ALL_BUCKET_ID,
            bucketName = null,
            coverUri = media.first().uri,
            coverMediaType = media.first().mediaType,
            mediaCount = media.size.toLong(),
        )
        listOf(all) + realDirectories.values
            .sortedWith(
                compareByDescending<MutableDirectory> { it.firstMediaDate }
                    .thenBy(MutableDirectory::bucketId),
            )
            .map(MutableDirectory::toAlbumDirectory)
    }

    private fun query(
        spec: MediaStoreQuerySpec,
        limit: Int?,
        offset: Int?,
    ): List<AlbumMedia> {
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, spec.selection)
                putStringArray(
                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    spec.selectionArgs.toTypedArray(),
                )
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, SORT_ORDER)
                limit?.let { putInt(ContentResolver.QUERY_ARG_LIMIT, it) }
                offset?.let { putInt(ContentResolver.QUERY_ARG_OFFSET, it) }
            }
            contentResolver.query(filesUri, PROJECTION, queryArgs, null)
        } else {
            val pageSuffix = if (limit != null && offset != null) {
                " LIMIT $limit OFFSET $offset"
            } else {
                ""
            }
            contentResolver.query(
                filesUri,
                PROJECTION,
                spec.selection,
                spec.selectionArgs.toTypedArray(),
                SORT_ORDER + pageSuffix,
            )
        }

        return cursor?.use(::readMedia) ?: emptyList()
    }

    private fun readMedia(cursor: Cursor): List<AlbumMedia> {
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val mediaTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
        val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
        val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
        val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
        val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)
        val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_ID)
        val bucketNameColumn =
            cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)

        return buildList(cursor.count) {
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val mediaType = cursor.readMediaType(mediaTypeColumn)
                add(
                    AlbumMedia(
                        uri = ContentUris.withAppendedId(mediaType.contentUri, id),
                        mediaType = mediaType,
                        displayName = cursor.stringOrNull(displayNameColumn),
                        mimeType = cursor.stringOrNull(mimeTypeColumn),
                        sizeBytes = cursor.positiveLongOrNull(sizeColumn),
                        dateAddedEpochSeconds = cursor.positiveLongOrNull(dateAddedColumn),
                        dateModifiedEpochSeconds = cursor.positiveLongOrNull(dateModifiedColumn),
                        width = cursor.positiveIntOrNull(widthColumn),
                        height = cursor.positiveIntOrNull(heightColumn),
                        durationMillis = if (mediaType == AlbumMediaType.VIDEO) {
                            cursor.positiveLongOrNull(durationColumn)
                        } else {
                            null
                        },
                        bucketId = cursor.longOrNull(bucketIdColumn),
                        bucketName = cursor.stringOrNull(bucketNameColumn),
                        selectedAtEpochMillis = null,
                        source = AlbumMediaSource.MEDIA_STORE,
                    ),
                )
            }
        }
    }

    private fun Cursor.readMediaType(column: Int): AlbumMediaType = when (getInt(column)) {
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> AlbumMediaType.IMAGE
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> AlbumMediaType.VIDEO
        else -> error("Unsupported MediaStore media type: ${getInt(column)}")
    }

    private fun Cursor.stringOrNull(column: Int): String? =
        if (isNull(column)) null else getString(column)

    private fun Cursor.longOrNull(column: Int): Long? =
        if (isNull(column)) null else getLong(column)

    private fun Cursor.positiveLongOrNull(column: Int): Long? =
        longOrNull(column)?.takeIf { it > 0L }

    private fun Cursor.positiveIntOrNull(column: Int): Int? =
        if (isNull(column)) null else getInt(column).takeIf { it > 0 }

    private val AlbumMediaType.contentUri: Uri
        get() = when (this) {
            AlbumMediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            AlbumMediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    private data class MutableDirectory(
        val bucketId: Long,
        val bucketName: String?,
        val coverUri: Uri,
        val coverMediaType: AlbumMediaType,
        val firstMediaDate: Long,
        var mediaCount: Long = 0,
    ) {
        fun toAlbumDirectory() = AlbumDirectory(
            bucketId = bucketId,
            bucketName = bucketName,
            coverUri = coverUri,
            coverMediaType = coverMediaType,
            mediaCount = mediaCount,
        )
    }

    private companion object {
        const val EXTERNAL_VOLUME = "external"
        const val SORT_ORDER = "date_added DESC, _id DESC"

        val PROJECTION = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.Video.VideoColumns.DURATION,
            MediaStore.Images.ImageColumns.BUCKET_ID,
            MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
        )
    }
}
