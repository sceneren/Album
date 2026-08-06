package com.github.sceneren.album.ui.view

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.github.sceneren.album.api.AlbumDirectory

internal class DirectoryHolder(
    itemView: View,
    private val imageLoader: AlbumImageLoader,
    private val onClick: (Long) -> Unit,
) : RecyclerView.ViewHolder(itemView) {
    private val cover: ImageView = itemView.findViewById(R.id.auv_directory_cover)
    private val label: TextView = itemView.findViewById(R.id.auv_directory_label)

    fun bind(directory: AlbumDirectory, selected: Boolean) {
        clear()
        val name = if (directory.bucketId == AlbumDirectory.ALL_BUCKET_ID) {
            itemView.context.getString(R.string.auv_all_media)
        } else {
            directory.bucketName?.takeIf(String::isNotBlank)
                ?: itemView.context.getString(R.string.auv_unnamed_directory_name)
        }
        label.text = itemView.context.getString(
            R.string.auv_directory_label,
            name,
            directory.mediaCount,
        )
        itemView.setBackgroundColor(
            ContextCompat.getColor(
                itemView.context,
                if (selected) {
                    R.color.auv_directory_selected
                } else {
                    R.color.auv_directory_panel
                },
            ),
        )
        imageLoader.load(cover, directory.toCoverMedia(), AlbumImageTarget.GRID_THUMBNAIL)
        itemView.setOnClickListener { onClick(directory.bucketId) }
    }

    fun clear() {
        imageLoader.clear(cover)
        label.text = null
        itemView.setOnClickListener(null)
    }
}
