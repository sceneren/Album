package com.github.sceneren.album.ui.view

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.github.sceneren.album.api.AlbumDirectory

/** 持有并绑定 `DirectoryHolder` 对应的列表项视图。 */
internal class DirectoryHolder(
    itemView: View,
    private val imageLoader: AlbumImageLoader,
    private val onClick: (Long) -> Unit,
) : RecyclerView.ViewHolder(itemView) {
    private val cover: ImageView = itemView.findViewById(R.id.auv_directory_cover)
    private val label: TextView = itemView.findViewById(R.id.auv_directory_label)
    private val selectedBackground = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(ContextCompat.getColor(itemView.context, R.color.auv_directory_selected))
        cornerRadius = itemView.resources.getDimension(R.dimen.auv_directory_corner_radius)
    }

    /** 执行 `bind` 方法定义的处理。 */
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
        itemView.background = selectedBackground.takeIf { selected }
        imageLoader.load(cover, directory.toCoverMedia(), AlbumImageTarget.GRID_THUMBNAIL)
        itemView.setOnClickListener { onClick(directory.bucketId) }
    }

    /** 清理 `clear` 对应的数据或资源。 */
    fun clear() {
        imageLoader.clear(cover)
        label.text = null
        itemView.setOnClickListener(null)
    }
}
