package com.github.sceneren.album.ui.view

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.sceneren.album.api.AlbumMedia

/** 管理可变预览分页数据，并在追加媒体页时执行去重。 */
internal class PreviewAdapter(
    private val appearance: AlbumPickerAppearance,
    private val imageLoader: AlbumImageLoader,
    initialItems: List<AlbumMedia>,
) : RecyclerView.Adapter<PreviewHolder>() {
    private val items = initialItems.distinctBy { it.uri }.toMutableList()
    private val uris = items.mapTo(linkedSetOf()) { it.uri }

    /** 处理 `onCreateViewHolder` 回调。 */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewHolder =
        PreviewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.auv_item_album_preview_page, parent, false),
            appearance,
            imageLoader,
        )

    /** 处理 `onBindViewHolder` 回调。 */
    override fun onBindViewHolder(holder: PreviewHolder, position: Int) {
        holder.bind(items[position])
    }

    /** 处理 `onViewRecycled` 回调。 */
    override fun onViewRecycled(holder: PreviewHolder) {
        holder.clear()
    }

    /** 获取 `getItemCount` 所需的数据。 */
    override fun getItemCount(): Int = items.size

    /** 执行 `itemAt` 方法定义的处理。 */
    fun itemAt(position: Int): AlbumMedia? = items.getOrNull(position)

    /** 执行 `append` 方法定义的处理。 */
    fun append(page: List<AlbumMedia>) {
        val additions = page.filter { uris.add(it.uri) }
        if (additions.isEmpty()) return
        val start = items.size
        items += additions
        notifyItemRangeInserted(start, additions.size)
    }
}
