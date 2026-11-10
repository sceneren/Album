package com.github.sceneren.album.ui.view

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.ui.view.internal.video.PreviewVideoOutput

/** Manages preview paging and reports only the attached, selected video as a playback output. */
internal class PreviewAdapter(
    private val appearance: AlbumPickerAppearance,
    private val imageLoader: AlbumImageLoader,
    initialItems: List<AlbumMedia>,
    private val videoPlaybackEnabled: Boolean = false,
    private val onVideoOutputChanged: (PreviewVideoOutput?) -> Unit = {},
) : RecyclerView.Adapter<PreviewHolder>() {
    private val items = initialItems.distinctBy { it.uri }.toMutableList()
    private val uris = items.mapTo(linkedSetOf()) { it.uri }
    private val attached = linkedSetOf<PreviewHolder>()
    private var selectedPosition = RecyclerView.NO_POSITION
    private var reportedOutput: PreviewHolder? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewHolder =
        PreviewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.auv_item_album_preview_page, parent, false),
            appearance,
            imageLoader,
            videoPlaybackEnabled,
        )

    override fun onBindViewHolder(holder: PreviewHolder, position: Int) {
        // Release before clear/rebind removes an active engine's rendering surface.
        if (reportedOutput === holder) reportOutput(null)
        holder.bind(items[position])
        updateOutput()
    }

    override fun onViewAttachedToWindow(holder: PreviewHolder) {
        attached += holder
        updateOutput()
    }

    override fun onViewDetachedFromWindow(holder: PreviewHolder) {
        attached -= holder
        updateOutput()
    }

    override fun onViewRecycled(holder: PreviewHolder) {
        attached -= holder
        updateOutput()
        holder.clear()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        reportOutput(null)
        attached.forEach(PreviewHolder::clear)
        attached.clear()
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun getItemCount(): Int = items.size

    fun itemAt(position: Int): AlbumMedia? = items.getOrNull(position)

    fun selectPosition(position: Int) {
        selectedPosition = position
        updateOutput()
    }

    fun append(page: List<AlbumMedia>) {
        val additions = page.filter { uris.add(it.uri) }
        if (additions.isEmpty()) return
        val start = items.size
        items += additions
        notifyItemRangeInserted(start, additions.size)
    }

    private fun updateOutput() {
        val media = items.getOrNull(selectedPosition)
        val target = if (videoPlaybackEnabled && media?.mediaType == AlbumMediaType.VIDEO) {
            attached.firstOrNull { it.boundMedia?.uri == media.uri }
        } else {
            null
        }
        reportOutput(target)
    }

    private fun reportOutput(holder: PreviewHolder?) {
        if (reportedOutput === holder) return
        reportedOutput = holder
        onVideoOutputChanged(holder)
    }
}
