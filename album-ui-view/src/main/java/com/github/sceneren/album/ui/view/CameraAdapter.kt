package com.github.sceneren.album.ui.view

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.sceneren.album.api.AlbumMedia

/** Renders camera captures that are prepended to a full-access gallery. */
internal class CameraAdapter(
    private val appearance: AlbumPickerAppearance,
    private val gridMetrics: GridMetrics,
    private val imageLoader: AlbumImageLoader,
    private val maxSelectionCount: Int,
    private val onPreview: (AlbumMedia) -> Unit,
    private val onToggle: (AlbumMedia) -> Unit,
) : RecyclerView.Adapter<MediaHolder>() {
    private var items: List<AlbumMedia> = emptyList()
    private var selectedUris: Set<Uri> = emptySet()

    fun submit(value: List<AlbumMedia>, selectedUris: Set<Uri>) {
        if (items != value) {
            val previousItems = items
            val diff = DiffUtil.calculateDiff(
                object : DiffUtil.Callback() {
                    override fun getOldListSize(): Int = previousItems.size

                    override fun getNewListSize(): Int = value.size

                    override fun areItemsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int,
                    ): Boolean = previousItems[oldItemPosition].uri == value[newItemPosition].uri

                    override fun areContentsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int,
                    ): Boolean = previousItems[oldItemPosition] == value[newItemPosition]
                },
            )
            items = value
            diff.dispatchUpdatesTo(this)
        }
        updateSelection(selectedUris)
    }

    fun currentItems(): List<AlbumMedia> = items

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
        val item = items[position]
        holder.bind(
            media = item,
            selected = item.uri in selectedUris,
            selectionBlocked = selectionLimitReached() && item.uri !in selectedUris,
            onPreview = onPreview,
            onToggle = onToggle,
        )
    }

    override fun onBindViewHolder(
        holder: MediaHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (SELECTION_STATE_PAYLOAD in payloads) {
            val item = items[position]
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

    override fun getItemCount(): Int = items.size

    private fun updateSelection(value: Set<Uri>) {
        val changedUris = (selectedUris - value) + (value - selectedUris)
        if (changedUris.isEmpty()) return
        val limitStateChanged = selectionLimitReached() != selectionLimitReached(value)
        selectedUris = value
        if (limitStateChanged) {
            notifyItemRangeChanged(0, itemCount, SELECTION_STATE_PAYLOAD)
            return
        }
        items.forEachIndexed { index, item ->
            if (item.uri in changedUris) {
                notifyItemChanged(index, SELECTION_STATE_PAYLOAD)
            }
        }
    }

    private fun selectionLimitReached(value: Set<Uri> = selectedUris): Boolean =
        value.size >= maxSelectionCount

    private companion object {
        val SELECTION_STATE_PAYLOAD = Any()
    }
}
