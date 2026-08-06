package com.github.sceneren.album.ui.view

import android.graphics.Color
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.github.sceneren.album.api.AlbumMedia

internal class MediaHolder(
    itemView: View,
    private val appearance: AlbumPickerAppearance,
    private val imageLoader: AlbumImageLoader,
    cellSize: Int,
) : RecyclerView.ViewHolder(itemView) {
    private val image: ImageView = itemView.findViewById(R.id.auv_media_image)
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
        selected: Set<Uri>,
        onPreview: (AlbumMedia) -> Unit,
        onToggle: (AlbumMedia) -> Unit,
    ) {
        if (boundMedia != media) {
            imageLoader.clear(image)
            imageLoader.load(image, media, AlbumImageTarget.GRID_THUMBNAIL)
            boundMedia = media
        }
        val checked = media.uri in selected
        val checkIcon = if (checked) {
            appearance.checkedIconRes ?: R.drawable.auv_ic_album_checked
        } else {
            appearance.uncheckedIconRes ?: R.drawable.auv_ic_album_unchecked
        }
        check.setImageResource(checkIcon)
        check.setBackgroundColor(appearance.scrimColor ?: Color.TRANSPARENT)
        check.setOnClickListener { onToggle(media) }
        itemView.setOnClickListener { onPreview(media) }
    }

    fun clear() {
        boundMedia = null
        imageLoader.clear(image)
        check.setImageDrawable(null)
        check.setBackgroundColor(Color.TRANSPARENT)
        check.setOnClickListener(null)
        itemView.setOnClickListener(null)
    }
}
