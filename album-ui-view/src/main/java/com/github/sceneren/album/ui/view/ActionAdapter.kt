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

    /** 执行 `submit` 方法定义的处理。 */
    fun submit(value: List<Action>) {
        if (items == value) return
        val previousItems = items
        val diff = DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                /** 获取 `getOldListSize` 所需的数据。 */
                override fun getOldListSize(): Int = previousItems.size

                /** 获取 `getNewListSize` 所需的数据。 */
                override fun getNewListSize(): Int = value.size

                /** 执行 `areItemsTheSame` 方法定义的处理。 */
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                    previousItems[oldItemPosition] == value[newItemPosition]

                /** 执行 `areContentsTheSame` 方法定义的处理。 */
                override fun areContentsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int,
                ): Boolean = areItemsTheSame(oldItemPosition, newItemPosition)
            },
        )
        items = value
        diff.dispatchUpdatesTo(this)
    }

    /** 处理 `onCreateViewHolder` 回调。 */
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

    /** 处理 `onBindViewHolder` 回调。 */
    override fun onBindViewHolder(holder: ActionHolder, position: Int) {
        holder.bind(items[position], onClick)
    }

    /** 获取 `getItemCount` 所需的数据。 */
    override fun getItemCount(): Int = items.size
}
