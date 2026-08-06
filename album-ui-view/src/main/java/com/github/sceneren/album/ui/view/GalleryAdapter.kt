package com.github.sceneren.album.ui.view

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.github.sceneren.album.api.AlbumMedia

/** 为 MediaStore 或持久化选择器媒体提供分页适配。 */
internal class GalleryAdapter(
    private val appearance: AlbumPickerAppearance,
    private val gridMetrics: GridMetrics,
    private val imageLoader: AlbumImageLoader,
    private val maxSelectionCount: Int,
    private val onPreview: (AlbumMedia) -> Unit,
    private val onToggle: (AlbumMedia) -> Unit,
) : PagingDataAdapter<AlbumMedia, MediaHolder>(DIFF) {
    private var selectedUris: Set<Uri> = emptySet()

    /** 更新 `updateSelection` 对应的状态。 */
    fun updateSelection(value: Set<Uri>) {
        val changedUris = (selectedUris - value) + (value - selectedUris)
        if (changedUris.isEmpty()) return
        val limitStateChanged = selectionLimitReached() != selectionLimitReached(value)
        selectedUris = value
        if (limitStateChanged) {
            notifyItemRangeChanged(0, itemCount, SELECTION_STATE_PAYLOAD)
            return
        }
        for (index in 0 until itemCount) {
            if (peek(index)?.uri in changedUris) {
                notifyItemChanged(index, SELECTION_STATE_PAYLOAD)
            }
        }
    }

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
        val item = getItem(position)
        if (item != null) {
            holder.bind(
                media = item,
                selected = item.uri in selectedUris,
                selectionBlocked = selectionLimitReached() && item.uri !in selectedUris,
                onPreview = onPreview,
                onToggle = onToggle,
            )
        } else {
            holder.clear()
        }
    }

    /** 处理 `onBindViewHolder` 回调。 */
    override fun onBindViewHolder(
        holder: MediaHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        val item = peek(position)
        if (item != null && SELECTION_STATE_PAYLOAD in payloads) {
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

    /** 提供类级共享常量与工厂能力。 */
    private companion object {
        /** 表示 `SELECTION_STATE_PAYLOAD` 对应的数据。 */
        val SELECTION_STATE_PAYLOAD = Any()

        /** 表示 `DIFF` 对应的数据。 */
        val DIFF = object : DiffUtil.ItemCallback<AlbumMedia>() {
            /** 执行 `areItemsTheSame` 方法定义的处理。 */
            override fun areItemsTheSame(oldItem: AlbumMedia, newItem: AlbumMedia): Boolean =
                oldItem.uri == newItem.uri

            /** 执行 `areContentsTheSame` 方法定义的处理。 */
            override fun areContentsTheSame(oldItem: AlbumMedia, newItem: AlbumMedia): Boolean =
                oldItem == newItem
        }
    }

    /** 执行 `selectionLimitReached` 方法定义的处理。 */
    private fun selectionLimitReached(value: Set<Uri> = selectedUris): Boolean =
        value.size >= maxSelectionCount
}
