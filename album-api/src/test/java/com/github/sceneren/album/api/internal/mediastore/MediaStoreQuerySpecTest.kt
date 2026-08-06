package com.github.sceneren.album.api.internal.mediastore

import android.provider.MediaStore
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMediaFilter
import org.junit.Assert.assertEquals
import org.junit.Test

/** 验证 `MediaStoreQuerySpecTest` 覆盖的行为。 */
class MediaStoreQuerySpecTest {
    @Test
    /** 验证 `mixedFilterAndBucketComposeOneSelection` 所描述的场景。 */
    fun mixedFilterAndBucketComposeOneSelection() {
        val spec = MediaStoreQuerySpec.create(
            AlbumMediaFilter.IMAGES_AND_VIDEOS,
            bucketId = 42L,
        )

        assertEquals(
            "(media_type IN (?,?)) AND (bucket_id = ?)",
            spec.selection,
        )
        assertEquals(
            listOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                "42",
            ),
            spec.selectionArgs,
        )
    }

    @Test
    /** 验证 `allBucketOmitsBucketPredicate` 所描述的场景。 */
    fun allBucketOmitsBucketPredicate() {
        val spec = MediaStoreQuerySpec.create(
            AlbumMediaFilter.VIDEOS,
            AlbumDirectory.ALL_BUCKET_ID,
        )

        assertEquals("media_type = ?", spec.selection)
        assertEquals(
            listOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()),
            spec.selectionArgs,
        )
    }
}
