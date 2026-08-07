package com.github.sceneren.album.api.internal.mediastore

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.api.resolveAlbumMediaSpecialFormat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 负责 `AndroidMediaStoreDataSource` 相关的数据与行为。 */
internal class AndroidMediaStoreDataSource(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MediaStoreDataSource {
    private val contentResolver = context.applicationContext.contentResolver
    private val filesUri = MediaStore.Files.getContentUri(EXTERNAL_VOLUME)

    /** 获取 `loadAll` 所需的数据。 */
    override suspend fun loadAll(
        mediaFilter: AlbumMediaFilter,
    ): List<AlbumMedia> = withContext(ioDispatcher) {
        query(
            spec = MediaStoreQuerySpec.create(mediaFilter, AlbumDirectory.ALL_BUCKET_ID),
            limit = null,
            offset = null,
        )
    }

    /** 获取 `loadPage` 所需的数据。 */
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

    /** 获取 `getDirectories` 所需的数据。 */
    override suspend fun getDirectories(
        mediaFilter: AlbumMediaFilter,
    ): List<AlbumDirectory> = withContext(ioDispatcher) {
        queryDirectories(
            MediaStoreQuerySpec.create(mediaFilter, AlbumDirectory.ALL_BUCKET_ID),
        )
    }

    /** 获取 `query` 所需的数据。 */
    private fun query(
        spec: MediaStoreQuerySpec,
        limit: Int?,
        offset: Int?,
    ): List<AlbumMedia> {
        val cursor = queryCursor(spec, mediaProjection(), limit, offset)
        return cursor?.use(::readMedia) ?: emptyList()
    }

    /** 获取 `queryDirectories` 所需的数据。 */
    private fun queryDirectories(spec: MediaStoreQuerySpec): List<AlbumDirectory> {
        val cursor = queryCursor(
            spec = spec,
            projection = DIRECTORY_PROJECTION,
            limit = null,
            offset = null,
        )
        return cursor?.use(::readDirectories) ?: emptyList()
    }

    /** 获取 `queryCursor` 所需的数据。 */
    private fun queryCursor(
        spec: MediaStoreQuerySpec,
        projection: Array<String>,
        limit: Int?,
        offset: Int?,
    ): Cursor? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
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
            contentResolver.query(filesUri, projection, queryArgs, null)
        } else {
            val pageSuffix = if (limit != null && offset != null) {
                " LIMIT $limit OFFSET $offset"
            } else {
                ""
            }
            contentResolver.query(
                filesUri,
                projection,
                spec.selection,
                spec.selectionArgs.toTypedArray(),
                SORT_ORDER + pageSuffix,
            )
        }

    /** 获取 `readMedia` 所需的数据。 */
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
        val specialFormatColumn = cursor.getColumnIndex(SPECIAL_FORMAT_COLUMN)
        val xmpColumn = cursor.getColumnIndex(MediaStore.MediaColumns.XMP)

        return buildList(cursor.count) {
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val mediaType = cursor.readMediaType(mediaTypeColumn)
                val displayName = cursor.stringOrNull(displayNameColumn)
                val mimeType = cursor.stringOrNull(mimeTypeColumn)
                add(
                    AlbumMedia(
                        uri = ContentUris.withAppendedId(mediaType.contentUri, id),
                        mediaType = mediaType,
                        displayName = displayName,
                        mimeType = mimeType,
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
                        specialFormat = resolveAlbumMediaSpecialFormat(
                            specialFormatCode = cursor.intOrNull(specialFormatColumn),
                            mimeType = mimeType,
                            displayName = displayName,
                            xmp = cursor.blobOrNull(xmpColumn),
                        ),
                    ),
                )
            }
        }
    }

    /** 获取 `readDirectories` 所需的数据。 */
    private fun readDirectories(cursor: Cursor): List<AlbumDirectory> {
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val mediaTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
        val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_ID)
        val bucketNameColumn =
            cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME)
        val realDirectories = LinkedHashMap<Long, MutableDirectory>()
        var firstUri: Uri? = null
        var firstType: AlbumMediaType? = null
        var totalCount = 0L

        while (cursor.moveToNext()) {
            val mediaType = cursor.readMediaType(mediaTypeColumn)
            val mediaUri = ContentUris.withAppendedId(
                mediaType.contentUri,
                cursor.getLong(idColumn),
            )
            if (firstUri == null) {
                firstUri = mediaUri
                firstType = mediaType
            }
            totalCount++

            val bucketId = cursor.longOrNull(bucketIdColumn) ?: continue
            val directory = realDirectories.getOrPut(bucketId) {
                MutableDirectory(
                    bucketId = bucketId,
                    bucketName = cursor.stringOrNull(bucketNameColumn),
                    coverUri = mediaUri,
                    coverMediaType = mediaType,
                    firstMediaDate = cursor.positiveLongOrNull(dateAddedColumn)
                        ?: Long.MIN_VALUE,
                )
            }
            directory.mediaCount++
        }

        val coverUri = firstUri ?: return emptyList()
        val coverType = checkNotNull(firstType)
        val all = AlbumDirectory(
            bucketId = AlbumDirectory.ALL_BUCKET_ID,
            bucketName = null,
            coverUri = coverUri,
            coverMediaType = coverType,
            mediaCount = totalCount,
        )
        return listOf(all) + realDirectories.values
            .sortedWith(
                compareByDescending<MutableDirectory> { it.firstMediaDate }
                    .thenBy(MutableDirectory::bucketId),
            )
            .map(MutableDirectory::toAlbumDirectory)
    }

    /** 提供类级共享常量与工厂能力。 */
    private companion object {
        /** 表示 `EXTERNAL_VOLUME` 对应的数据。 */
        const val EXTERNAL_VOLUME = "external"
        /** 表示 `SORT_ORDER` 对应的数据。 */
        const val SORT_ORDER = "date_added DESC, _id DESC"

        /** 表示 `PROJECTION` 对应的数据。 */
        private val BASE_MEDIA_PROJECTION = arrayOf(
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

        private fun mediaProjection(): Array<String> = when {
            supportsSpecialFormatColumn() -> BASE_MEDIA_PROJECTION + SPECIAL_FORMAT_COLUMN
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                BASE_MEDIA_PROJECTION + MediaStore.MediaColumns.XMP
            }
            else -> BASE_MEDIA_PROJECTION
        }

        private fun supportsSpecialFormatColumn(): Boolean =
            Build.VERSION.SDK_INT >= API_LEVEL_WITH_SPECIAL_FORMAT ||
                (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >=
                        SPECIAL_FORMAT_S_EXTENSION_VERSION
                )

        /** 表示 `DIRECTORY_PROJECTION` 对应的数据。 */
        val DIRECTORY_PROJECTION = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.Images.ImageColumns.BUCKET_ID,
            MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
        )

        private const val SPECIAL_FORMAT_COLUMN = "_special_format"
        private const val API_LEVEL_WITH_SPECIAL_FORMAT = 37
        private const val SPECIAL_FORMAT_S_EXTENSION_VERSION = 21
    }
}
