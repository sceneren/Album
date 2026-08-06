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
    private val onPreview: (AlbumMedia) -> Unit,
    private val onToggle: (AlbumMedia) -> Unit,
) : PagingDataAdapter<AlbumMedia, MediaHolder>(DIFF) {
    private var selectedUris: Set<Uri> = emptySet()

    fun updateSelection(value: Set<Uri>) {
        val changedUris = (selectedUris - value) + (value - selectedUris)
        if (changedUris.isEmpty()) return
        selectedUris = value
        for (index in 0 until itemCount) {
            if (peek(index)?.uri in changedUris) notifyItemChanged(index)
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
            holder.bind(item, selectedUris, onPreview, onToggle)
        } else {
            holder.clear()
        }
    }

    override fun onViewRecycled(holder: MediaHolder) {
        holder.clear()
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<AlbumMedia>() {
            override fun areItemsTheSame(oldItem: AlbumMedia, newItem: AlbumMedia): Boolean =
                oldItem.uri == newItem.uri

            override fun areContentsTheSame(oldItem: AlbumMedia, newItem: AlbumMedia): Boolean =
                oldItem == newItem
        }
    }
}
