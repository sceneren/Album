package com.github.sceneren.album.ui.view

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.sceneren.album.api.AlbumDirectory

/** Renders MediaStore directories and tracks the selected bucket. */
internal class DirectoryAdapter(
    private val imageLoader: AlbumImageLoader,
    private val onClick: (Long) -> Unit,
) : RecyclerView.Adapter<DirectoryHolder>() {
    private var items: List<AlbumDirectory> = emptyList()
    private var selectedBucketId: Long = AlbumDirectory.ALL_BUCKET_ID

    fun submit(value: List<AlbumDirectory>, selectedBucketId: Long) {
        if (items == value && this.selectedBucketId == selectedBucketId) return
        val previousItems = items
        val previousSelectedBucketId = this.selectedBucketId
        val diff = DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = previousItems.size

                override fun getNewListSize(): Int = value.size

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                    previousItems[oldItemPosition].bucketId == value[newItemPosition].bucketId

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

    override fun onBindViewHolder(holder: DirectoryHolder, position: Int) {
        val directory = items[position]
        holder.bind(directory, directory.bucketId == selectedBucketId)
    }

    override fun onViewRecycled(holder: DirectoryHolder) {
        holder.clear()
    }

    override fun getItemCount(): Int = items.size
}
