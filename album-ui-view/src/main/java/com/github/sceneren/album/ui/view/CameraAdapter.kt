package com.github.sceneren.album.ui.view

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.sceneren.album.api.AlbumMedia

/** 渲染追加到完整授权图库前部的相机拍摄结果。 */
internal class CameraAdapter(
    private val appearance: AlbumPickerAppearance,
    private val gridMetrics: GridMetrics,
    private val imageLoader: AlbumImageLoader,
    private val maxSelectionCount: Int,
    private val onPreview: (AlbumMedia) -> Unit,
    private val onToggle: (AlbumMedia) -> Unit,
) : RecyclerView.Adapter<MediaHolder>() {
    private var items: List<AlbumMedia> = emptyList()
    private var selectedUris: Set<Uri> = emptySet()

    /** 执行 `submit` 方法定义的处理。 */
    fun submit(value: List<AlbumMedia>, selectedUris: Set<Uri>) {
        if (items != value) {
            val previousItems = items
            val diff = DiffUtil.calculateDiff(
                object : DiffUtil.Callback() {
                    /** 获取 `getOldListSize` 所需的数据。 */
                    override fun getOldListSize(): Int = previousItems.size

                    /** 获取 `getNewListSize` 所需的数据。 */
                    override fun getNewListSize(): Int = value.size

                    /** 执行 `areItemsTheSame` 方法定义的处理。 */
                    override fun areItemsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int,
                    ): Boolean = previousItems[oldItemPosition].uri == value[newItemPosition].uri

                    /** 执行 `areContentsTheSame` 方法定义的处理。 */
                    override fun areContentsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int,
                    ): Boolean = previousItems[oldItemPosition] == value[newItemPosition]
                },
            )
            items = value
            diff.dispatchUpdatesTo(this)
        }
        updateSelection(selectedUris)
    }

    /** 执行 `currentItems` 方法定义的处理。 */
    fun currentItems(): List<AlbumMedia> = items

    /** 处理 `onCreateViewHolder` 回调。 */
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

    /** 处理 `onBindViewHolder` 回调。 */
    override fun onBindViewHolder(holder: MediaHolder, position: Int) {
        val item = items[position]
        holder.bind(
            media = item,
            selected = item.uri in selectedUris,
            selectionBlocked = selectionLimitReached() && item.uri !in selectedUris,
            onPreview = onPreview,
            onToggle = onToggle,
        )
    }

    /** 处理 `onBindViewHolder` 回调。 */
    override fun onBindViewHolder(
        holder: MediaHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (SELECTION_STATE_PAYLOAD in payloads) {
            val item = items[position]
            holder.updateSelectionState(
                media = item,
                selected = item.uri in selectedUris,
                selectionBlocked = selectionLimitReached() && item.uri !in selectedUris,
                onPreview = onPreview,
                onToggle = onToggle,
            )
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    /** 处理 `onViewRecycled` 回调。 */
    override fun onViewRecycled(holder: MediaHolder) {
        holder.clear()
    }

    /** 获取 `getItemCount` 所需的数据。 */
    override fun getItemCount(): Int = items.size

    /** 更新 `updateSelection` 对应的状态。 */
    private fun updateSelection(value: Set<Uri>) {
        val changedUris = (selectedUris - value) + (value - selectedUris)
        if (changedUris.isEmpty()) return
        val limitStateChanged = selectionLimitReached() != selectionLimitReached(value)
        selectedUris = value
        if (limitStateChanged) {
            notifyItemRangeChanged(0, itemCount, SELECTION_STATE_PAYLOAD)
            return
        }
        items.forEachIndexed { index, item ->
            if (item.uri in changedUris) {
                notifyItemChanged(index, SELECTION_STATE_PAYLOAD)
            }
        }
    }

    /** 执行 `selectionLimitReached` 方法定义的处理。 */
    private fun selectionLimitReached(value: Set<Uri> = selectedUris): Boolean =
        value.size >= maxSelectionCount

    /** 提供类级共享常量与工厂能力。 */
    private companion object {
        /** 表示 `SELECTION_STATE_PAYLOAD` 对应的数据。 */
        val SELECTION_STATE_PAYLOAD = Any()
    }
}
