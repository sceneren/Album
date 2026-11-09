package com.github.sceneren.album.api.internal.picker

import android.net.Uri
import androidx.paging.PagingSource
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
/** 验证系统授权媒体分页源在失败重试、取消传播与偏移分页边界上的行为。 */
class AuthorizedMediaPagingSourceTest {
    @Test
    fun loadFailureCanRetryAndSucceedOnNextAttempt() = runTest {
        var attempts = 0
        val expected = listOf(media("1"), media("2"))
        val source = AuthorizedMediaPagingSource { offset, limit ->
            attempts++
            if (attempts == 1) {
                throw SecurityException("模拟授权查询失败")
            }
            expected.drop(offset).take(limit)
        }

        assertTrue(source.load(refresh()) is PagingSource.LoadResult.Error)
        val page = source.load(refresh()) as PagingSource.LoadResult.Page
        assertEquals(expected, page.data)
        assertEquals(2, attempts)
    }

    @Test
    fun cancellationIsNotConvertedToLoadError() = runTest {
        val source = AuthorizedMediaPagingSource { _, _ ->
            throw CancellationException("页面已关闭")
        }
        var cancelled = false
        try {
            source.load(refresh())
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    private fun refresh(): PagingSource.LoadParams.Refresh<Int> =
        PagingSource.LoadParams.Refresh(
            key = null,
            loadSize = 20,
            placeholdersEnabled = false,
        )

    private fun media(id: String) = AlbumMedia(
        uri = Uri.parse("content://picker/$id"),
        mediaType = AlbumMediaType.IMAGE,
        displayName = "$id.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 100L,
        dateAddedEpochSeconds = null,
        dateModifiedEpochSeconds = null,
        width = 100,
        height = 100,
        durationMillis = null,
        bucketId = null,
        bucketName = null,
        selectedAtEpochMillis = 1L,
        source = AlbumMediaSource.PHOTO_PICKER,
    )
}
