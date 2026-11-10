package com.github.sceneren.album.ui.view

import android.net.Uri
import androidx.paging.PagingData
import androidx.recyclerview.widget.RecyclerView
import com.github.sceneren.album.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GalleryRefreshTest {
    @Test fun unchangedGenerationEmitsNoUpdatesAndSingleEditOnlyUpdatesThatFile() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val adapter = GalleryAdapter(AlbumPickerAppearance(), GridMetrics(4, 1), AlbumImageLoader { _, _, _ -> }, 9, {}, {})
            val changes = mutableListOf<Pair<Int, Int>>()
            var resets = 0
            adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() { resets++ }
                override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                    changes += positionStart to itemCount
                }
            })
            val original = (0 until 100).map { id ->
                AlbumMedia(Uri.parse("content://media/$id"), AlbumMediaType.IMAGE,
                    null, "image/jpeg", 100, null, 1, 800, 600, null, null, null, null, AlbumMediaSource.MEDIA_STORE)
            }
            adapter.submitData(PagingData.from(original))
            adapter.submitData(PagingData.from(original.toList()))
            assertEquals(emptyList<Pair<Int, Int>>(), changes)
            val updated = original.mapIndexed { index, item -> if (index == 48) item.copy(generationModified = 2) else item }
            adapter.submitData(PagingData.from(updated))
            assertEquals(listOf(48 to 1), changes)
            assertEquals(0, resets)
            assertEquals(updated, adapter.snapshot().items)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
