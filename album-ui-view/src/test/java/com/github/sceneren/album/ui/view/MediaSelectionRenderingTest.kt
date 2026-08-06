package com.github.sceneren.album.ui.view

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaSelectionRenderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val first = media("content://media/1")
    private val second = media("content://media/2")

    @Test
    fun selectionStateUpdateDoesNotReloadThumbnail() {
        var loadCount = 0
        var previewCount = 0
        val holder = holder(
            AlbumImageLoader { _, _, _ -> loadCount++ },
        )

        holder.bind(first, selected = false, selectionBlocked = false, { previewCount++ }, {})
        holder.updateSelectionState(
            first,
            selected = true,
            selectionBlocked = false,
            { previewCount++ },
            {},
        )
        holder.itemView.findViewById<View>(R.id.auv_media_scrim).performClick()

        assertEquals(1, loadCount)
        assertEquals(1, previewCount)
        assertEquals(View.VISIBLE, holder.itemView.findViewById<View>(R.id.auv_media_scrim).visibility)
    }

    @Test
    fun blockedTileDoesNotPreviewAndItsCheckStillHandlesToggle() {
        var previewCount = 0
        var toggleCount = 0
        val holder = holder(AlbumImageLoader { _, _, _ -> })
        holder.bind(
            media = first,
            selected = false,
            selectionBlocked = true,
            onPreview = { previewCount++ },
            onToggle = { toggleCount++ },
        )

        holder.itemView.performClick()
        holder.itemView.findViewById<View>(R.id.auv_media_scrim).performClick()
        holder.itemView.findViewById<View>(R.id.auv_media_check).performClick()

        assertEquals(0, previewCount)
        assertEquals(1, toggleCount)
    }

    @Test
    fun selectionCheckUses32DpTouchTargetWithUnchanged16DpIconArea() {
        val holder = holder(AlbumImageLoader { _, _, _ -> })
        val check = holder.itemView.findViewById<View>(R.id.auv_media_check)

        assertEquals(dp(32), check.layoutParams.width)
        assertEquals(dp(32), check.layoutParams.height)
        assertEquals(dp(8), check.paddingLeft)
        assertEquals(dp(8), check.paddingTop)
        assertEquals(dp(16), check.layoutParams.width - check.paddingLeft - check.paddingRight)
        assertEquals(dp(16), check.layoutParams.height - check.paddingTop - check.paddingBottom)
    }

    @Test
    fun adapterUsesPayloadForSingleAndLimitStateUpdates() {
        val adapter = CameraAdapter(
            appearance = AlbumPickerAppearance(),
            gridMetrics = GridMetrics(spanCount = 4, spacingPx = 1),
            imageLoader = AlbumImageLoader { _, _, _ -> },
            maxSelectionCount = 2,
            onPreview = {},
            onToggle = {},
        )
        val observer = RecordingObserver()
        adapter.registerAdapterDataObserver(observer)
        adapter.submit(listOf(first, second), emptySet())
        observer.changes.clear()

        adapter.submit(listOf(first, second), setOf(first.uri))

        assertEquals(listOf(Change(positionStart = 0, itemCount = 1)), observer.changes)
        observer.changes.clear()

        adapter.submit(listOf(first, second), setOf(first.uri, second.uri))

        assertEquals(listOf(Change(positionStart = 0, itemCount = 2)), observer.changes)
    }

    private fun holder(imageLoader: AlbumImageLoader): MediaHolder {
        val itemView = LayoutInflater.from(context).inflate(
            R.layout.auv_item_album_media,
            FrameLayout(context),
            false,
        )
        return MediaHolder(itemView, AlbumPickerAppearance(), imageLoader, cellSize = 100)
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private fun media(uri: String) = AlbumMedia(
        uri = Uri.parse(uri),
        mediaType = AlbumMediaType.IMAGE,
        displayName = null,
        mimeType = null,
        sizeBytes = null,
        dateAddedEpochSeconds = null,
        dateModifiedEpochSeconds = null,
        width = null,
        height = null,
        durationMillis = null,
        bucketId = null,
        bucketName = null,
        selectedAtEpochMillis = null,
        source = AlbumMediaSource.MEDIA_STORE,
    )

    private data class Change(
        val positionStart: Int,
        val itemCount: Int,
    )

    private class RecordingObserver : RecyclerView.AdapterDataObserver() {
        val changes = mutableListOf<Change>()

        override fun onItemRangeChanged(
            positionStart: Int,
            itemCount: Int,
            payload: Any?,
        ) {
            checkNotNull(payload)
            changes += Change(positionStart, itemCount)
        }
    }
}
