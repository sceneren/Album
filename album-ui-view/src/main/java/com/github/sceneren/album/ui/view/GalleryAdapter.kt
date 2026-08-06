package com.github.sceneren.album.ui.view

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.github.sceneren.album.api.AlbumMedia

/** Paging adapter for MediaStore or persisted picker media. */
internal class GalleryAdapter(
    private val appearance: AlbumPickerAppearance,
    private val gridMetrics: GridMetrics,
    private val imageLoader: AlbumImageLoader,
    private val maxSelectionCount: Int,
    private val onPreview: (AlbumMedia) -> Unit,
    private val onToggle: (AlbumMedia) -> Unit,
) : PagingDataAdapter<AlbumMedia, MediaHolder>(DIFF) {
    private var selectedUris: Set<Uri> = emptySet()

    fun updateSelection(value: Set<Uri>) {
        val changedUris = (selectedUris - value) + (value - selectedUris)
        if (changedUris.isEmpty()) return
        val limitStateChanged = selectionLimitReached() != selectionLimitReached(value)
        selectedUris = value
        if (limitStateChanged) {
            notifyItemRangeChanged(0, itemCount, SELECTION_STATE_PAYLOAD)
            return
        }
        for (index in 0 until itemCount) {
            if (peek(index)?.uri in changedUris) {
                notifyItemChanged(index, SELECTION_STATE_PAYLOAD)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaHolder =
        MediaHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.auv_item_album_media,
                parent,
                false,
            ),
            appearance,
            imageLoader,
            parent.gridCellSize(gridMetrics),
        )

    override fun onBindViewHolder(holder: MediaHolder, position: Int) {
        val item = getItem(position)
        if (item != null) {
            holder.bind(
                media = item,
                selected = item.uri in selectedUris,
                selectionBlocked = selectionLimitReached() && item.uri !in selectedUris,
                onPreview = onPreview,
                onToggle = onToggle,
            )
        } else {
            holder.clear()
        }
    }

    override fun onBindViewHolder(
        holder: MediaHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        val item = peek(position)
        if (item != null && SELECTION_STATE_PAYLOAD in payloads) {
            holder.updateSelectionState(
                media = item,
                selected = item.uri in selectedUris,
                selectionBlocked = selectionLimitReached() && item.uri !in selectedUris,
                onPreview = onPreview,
                onToggle = onToggle,
            )
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onViewRecycled(holder: MediaHolder) {
        holder.clear()
    }

    private companion object {
        val SELECTION_STATE_PAYLOAD = Any()

        val DIFF = object : DiffUtil.ItemCallback<AlbumMedia>() {
            override fun areItemsTheSame(oldItem: AlbumMedia, newItem: AlbumMedia): Boolean =
                oldItem.uri == newItem.uri

            override fun areContentsTheSame(oldItem: AlbumMedia, newItem: AlbumMedia): Boolean =
                oldItem == newItem
        }
    }

    private fun selectionLimitReached(value: Set<Uri> = selectedUris): Boolean =
        value.size >= maxSelectionCount
}
