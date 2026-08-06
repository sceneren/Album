package com.github.sceneren.album.ui.view

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.sceneren.album.api.AlbumMedia

/** Mutable preview pager data that appends deduplicated media pages. */
internal class PreviewAdapter(
    private val appearance: AlbumPickerAppearance,
    private val imageLoader: AlbumImageLoader,
    initialItems: List<AlbumMedia>,
) : RecyclerView.Adapter<PreviewHolder>() {
    private val items = initialItems.distinctBy { it.uri }.toMutableList()
    private val uris = items.mapTo(linkedSetOf()) { it.uri }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewHolder =
        PreviewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.auv_item_album_preview_page, parent, false),
            appearance,
            imageLoader,
        )

    override fun onBindViewHolder(holder: PreviewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun onViewRecycled(holder: PreviewHolder) {
        holder.clear()
    }

    override fun getItemCount(): Int = items.size

    fun itemAt(position: Int): AlbumMedia? = items.getOrNull(position)

    fun append(page: List<AlbumMedia>) {
        val additions = page.filter { uris.add(it.uri) }
        if (additions.isEmpty()) return
        val start = items.size
        items += additions
        notifyItemRangeInserted(start, additions.size)
    }
}
