package com.github.sceneren.album.ui.view

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** 持有并绑定 `ActionHolder` 对应的列表项视图。 */
internal class ActionHolder(
    itemView: View,
    private val appearance: AlbumPickerAppearance,
    cellSize: Int,
) : RecyclerView.ViewHolder(itemView) {
    private val icon: ImageView = itemView.findViewById(R.id.auv_action_icon)
    private val label: TextView = itemView.findViewById(R.id.auv_action_label)

    init {
        itemView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            cellSize,
        )
    }

    /** 执行 `bind` 方法定义的处理。 */
    fun bind(action: Action, onClick: (Action) -> Unit) {
        val context = itemView.context
        label.text = when (action) {
            Action.CAMERA -> context.getString(R.string.auv_capture)
            Action.ADD -> context.getString(R.string.auv_add_more)
        }
        val primary = appearance.primaryTextColor ?: context.color(R.color.auv_primary)
        label.setTextColor(primary)
        val customIcon = when (action) {
            Action.CAMERA -> appearance.cameraIconRes
            Action.ADD -> appearance.addIconRes
        }
        icon.setImageResource(
            customIcon ?: when (action) {
                Action.CAMERA -> R.drawable.auv_ic_album_camera
                Action.ADD -> R.drawable.auv_ic_album_add
            },
        )
        if (customIcon == null) icon.setColorFilter(primary) else icon.clearColorFilter()
        itemView.setOnClickListener { onClick(action) }
    }
}
