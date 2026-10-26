package com.github.sceneren.album.ui.compose

import android.net.Uri
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaSource
import com.github.sceneren.album.api.AlbumMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumPickerStateTest {
    @Test
    fun hostActionsRespectSelectionProcessingAndDisposal() {
        val state = AlbumPickerState()
        val owner = Any()
        var confirmations = 0
        var previews = 0
        var cancellations = 0
        val actions = AlbumPickerState.Actions(
            confirm = { confirmations++ }, preview = { previews++ },
            cancel = { cancellations++ }, refresh = {},
        )
        state.attach(owner)
        state.update(owner, emptyList(), false, actions)
        state.confirm()
        assertFalse(state.canConfirm)
        assertEquals(0, confirmations)

        state.update(owner, listOf(media()), false, actions)
        assertTrue(state.canConfirm)
        state.confirm()
        state.preview()
        assertEquals(1, confirmations)
        assertEquals(1, previews)

        state.update(owner, listOf(media()), true, actions)
        assertFalse(state.canConfirm)
        state.confirm()
        state.preview()
        state.cancel()
        assertEquals(1, confirmations)
        assertEquals(1, previews)
        assertEquals(0, cancellations)

        state.detach(owner)
        state.confirm()
        state.cancel()
        assertTrue(state.selectedItems.isEmpty())
        assertFalse(state.isProcessing)
        assertFalse(state.canConfirm)
        assertEquals(0, cancellations)
    }

    @Test
    fun stateRejectsConcurrentOwnersAndIgnoresStaleUpdates() {
        val state = AlbumPickerState()
        val first = Any()
        val second = Any()
        state.attach(first)
        assertThrows(IllegalStateException::class.java) { state.attach(second) }
        state.detach(first)
        state.attach(second)
        var confirmations = 0
        val callbacks = AlbumPickerState.Actions({ confirmations++ }, {}, {}, {})
        state.update(second, listOf(media()), false, callbacks)
        state.update(first, emptyList(), true, callbacks)
        state.detach(first)
        assertTrue(state.canConfirm)
        state.confirm()
        assertEquals(1, confirmations)
    }

    private fun media() = AlbumMedia(
        uri = Uri.parse("content://picker/test"), mediaType = AlbumMediaType.IMAGE,
        displayName = "test.jpg", mimeType = "image/jpeg", sizeBytes = null,
        dateAddedEpochSeconds = null, dateModifiedEpochSeconds = null,
        width = null, height = null, durationMillis = null,
        bucketId = null, bucketName = null, selectedAtEpochMillis = null,
        source = AlbumMediaSource.PHOTO_PICKER,
    )
}
