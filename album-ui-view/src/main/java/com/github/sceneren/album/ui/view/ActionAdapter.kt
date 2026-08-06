package com.github.sceneren.album.ui.view

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

/** XML 单元格的功能入口适配器。 */
internal class ActionAdapter(
    private val appearance: AlbumPickerAppearance,
    private val gridMetrics: GridMetrics,
    private val onClick: (Action) -> Unit,
) : RecyclerView.Adapter<ActionHolder>() {
    private var items: List<Action> = emptyList()

    fun submit(value: List<Action>) {
        if (items == value) return
        val previousItems = items
        val diff = DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = previousItems.size

                override fun getNewListSize(): Int = value.size

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                    previousItems[oldItemPosition] == value[newItemPosition]

                override fun areContentsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int,
                ): Boolean = areItemsTheSame(oldItemPosition, newItemPosition)
            },
        )
        items = value
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionHolder =
        ActionHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.auv_item_album_action,
                parent,
                false,
            ),
            appearance,
            parent.gridCellSize(gridMetrics),
        )

    override fun onBindViewHolder(holder: ActionHolder, position: Int) {
        holder.bind(items[position], onClick)
    }

    override fun getItemCount(): Int = items.size
}
