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
    private val onPreview: (AlbumMedia) -> Unit,
    private val onToggle: (AlbumMedia) -> Unit,
) : RecyclerView.Adapter<MediaHolder>() {
    private var items: List<AlbumMedia> = emptyList()
    private var selectedUris: Set<Uri> = emptySet()

    fun submit(value: List<AlbumMedia>, selectedUris: Set<Uri>) {
        if (items == value && this.selectedUris == selectedUris) return
        val previousItems = items
        val previousSelectedUris = this.selectedUris
        val diff = DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = previousItems.size

                override fun getNewListSize(): Int = value.size

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                    previousItems[oldItemPosition].uri == value[newItemPosition].uri

                override fun areContentsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int,
                ): Boolean {
                    val oldItem = previousItems[oldItemPosition]
                    val newItem = value[newItemPosition]
                    return oldItem == newItem &&
                        (oldItem.uri in previousSelectedUris) ==
                        (newItem.uri in selectedUris)
                }
            },
        )
        items = value
        this.selectedUris = selectedUris
        diff.dispatchUpdatesTo(this)
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
        holder.bind(items[position], selectedUris, onPreview, onToggle)
    }

    override fun onViewRecycled(holder: MediaHolder) {
        holder.clear()
    }

    override fun getItemCount(): Int = items.size
}
