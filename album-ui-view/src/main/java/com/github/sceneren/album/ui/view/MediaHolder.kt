package com.github.sceneren.album.ui.view

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.github.sceneren.album.api.AlbumMedia

internal class MediaHolder(
    itemView: View,
    private val appearance: AlbumPickerAppearance,
    private val imageLoader: AlbumImageLoader,
    cellSize: Int,
) : RecyclerView.ViewHolder(itemView) {
    private val image: ImageView = itemView.findViewById(R.id.auv_media_image)
    private val scrim: View = itemView.findViewById(R.id.auv_media_scrim)
    private val check: ImageView = itemView.findViewById(R.id.auv_media_check)
    private var boundMedia: AlbumMedia? = null

    init {
        itemView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            cellSize,
        )
    }

    fun bind(
        media: AlbumMedia,
        selected: Boolean,
        selectionBlocked: Boolean,
        onPreview: (AlbumMedia) -> Unit,
        onToggle: (AlbumMedia) -> Unit,
    ) {
        if (boundMedia != media) {
            imageLoader.clear(image)
            imageLoader.load(image, media, AlbumImageTarget.GRID_THUMBNAIL)
            boundMedia = media
        }
        updateSelectionState(media, selected, selectionBlocked, onPreview, onToggle)
    }

    fun updateSelectionState(
        media: AlbumMedia,
        selected: Boolean,
        selectionBlocked: Boolean,
        onPreview: (AlbumMedia) -> Unit,
        onToggle: (AlbumMedia) -> Unit,
    ) {
        val checkIcon = if (selected) {
            appearance.checkedIconRes ?: R.drawable.auv_ic_album_checked
        } else {
            appearance.uncheckedIconRes ?: R.drawable.auv_ic_album_unchecked
        }
        check.setImageResource(checkIcon)
        check.setBackgroundColor(Color.TRANSPARENT)
        check.setOnClickListener { onToggle(media) }
        scrim.isVisible = selected || selectionBlocked
        scrim.setBackgroundColor(
            when {
                selected -> appearance.scrimColor
                    ?: itemView.context.color(R.color.auv_media_selected_scrim)
                selectionBlocked -> itemView.context.color(R.color.auv_media_blocked_scrim)
                else -> Color.TRANSPARENT
            },
        )
        scrim.isClickable = selected || selectionBlocked
        scrim.setOnClickListener(
            when {
                selected -> View.OnClickListener { onPreview(media) }
                selectionBlocked -> View.OnClickListener { }
                else -> null
            },
        )
        itemView.setOnClickListener(
            if (selectionBlocked) null else View.OnClickListener { onPreview(media) },
        )
    }

    fun clear() {
        boundMedia = null
        imageLoader.clear(image)
        scrim.isVisible = false
        scrim.setBackgroundColor(Color.TRANSPARENT)
        scrim.isClickable = false
        scrim.setOnClickListener(null)
        check.setImageDrawable(null)
        check.setBackgroundColor(Color.TRANSPARENT)
        check.setOnClickListener(null)
        itemView.setOnClickListener(null)
    }
}
