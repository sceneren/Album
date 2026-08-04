package com.github.sceneren.album.api.internal.mediastore

import android.provider.MediaStore
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMediaFilter

internal data class MediaStoreQuerySpec(
    val selection: String,
    val selectionArgs: List<String>,
) {
    companion object {
        fun create(
            filter: AlbumMediaFilter,
            bucketId: Long,
        ): MediaStoreQuerySpec {
            val mediaTypeColumn = MediaStore.Files.FileColumns.MEDIA_TYPE
            val typeClause = when (filter) {
                AlbumMediaFilter.IMAGES -> "$mediaTypeColumn = ?" to
                    listOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
                AlbumMediaFilter.VIDEOS -> "$mediaTypeColumn = ?" to
                    listOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
                AlbumMediaFilter.IMAGES_AND_VIDEOS -> "$mediaTypeColumn IN (?,?)" to
                    listOf(
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                    )
            }

            return if (bucketId == AlbumDirectory.ALL_BUCKET_ID) {
                MediaStoreQuerySpec(typeClause.first, typeClause.second)
            } else {
                MediaStoreQuerySpec(
                    selection = "(${typeClause.first}) AND " +
                        "(${MediaStore.Images.ImageColumns.BUCKET_ID} = ?)",
                    selectionArgs = typeClause.second + bucketId.toString(),
                )
            }
        }
    }
}
