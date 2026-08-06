package com.github.sceneren.album.ui.view

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.sceneren.album.api.AlbumDirectory

/** 渲染 MediaStore 目录并跟踪当前选中的媒体桶。 */
internal class DirectoryAdapter(
    private val imageLoader: AlbumImageLoader,
    private val onClick: (Long) -> Unit,
) : RecyclerView.Adapter<DirectoryHolder>() {
    private var items: List<AlbumDirectory> = emptyList()
    private var selectedBucketId: Long = AlbumDirectory.ALL_BUCKET_ID

    /** 执行 `submit` 方法定义的处理。 */
    fun submit(value: List<AlbumDirectory>, selectedBucketId: Long) {
        if (items == value && this.selectedBucketId == selectedBucketId) return
        val previousItems = items
        val previousSelectedBucketId = this.selectedBucketId
        val diff = DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                /** 获取 `getOldListSize` 所需的数据。 */
                override fun getOldListSize(): Int = previousItems.size

                /** 获取 `getNewListSize` 所需的数据。 */
                override fun getNewListSize(): Int = value.size

                /** 执行 `areItemsTheSame` 方法定义的处理。 */
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                    previousItems[oldItemPosition].bucketId == value[newItemPosition].bucketId

                /** 执行 `areContentsTheSame` 方法定义的处理。 */
                override fun areContentsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int,
                ): Boolean {
                    val oldItem = previousItems[oldItemPosition]
                    val newItem = value[newItemPosition]
                    return oldItem == newItem &&
                        (oldItem.bucketId == previousSelectedBucketId) ==
                        (newItem.bucketId == selectedBucketId)
                }
            },
        )
        items = value
        this.selectedBucketId = selectedBucketId
        diff.dispatchUpdatesTo(this)
    }

    /** 处理 `onCreateViewHolder` 回调。 */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DirectoryHolder =
        DirectoryHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.auv_item_album_directory,
                parent,
                false,
            ),
            imageLoader,
            onClick,
        )

    /** 处理 `onBindViewHolder` 回调。 */
    override fun onBindViewHolder(holder: DirectoryHolder, position: Int) {
        val directory = items[position]
        holder.bind(directory, directory.bucketId == selectedBucketId)
    }

    /** 处理 `onViewRecycled` 回调。 */
    override fun onViewRecycled(holder: DirectoryHolder) {
        holder.clear()
    }

    /** 获取 `getItemCount` 所需的数据。 */
    override fun getItemCount(): Int = items.size
}
