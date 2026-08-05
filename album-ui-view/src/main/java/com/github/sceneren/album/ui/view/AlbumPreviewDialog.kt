package com.github.sceneren.album.ui.view

import android.app.Dialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.github.panpf.zoomimage.ZoomImageView
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 使用 XML、ViewPager2 和 ZoomImageView 实现的有界媒体预览。 */
internal class AlbumPreviewDialog(
    private val activity: AlbumPickerActivity,
    private val appearance: AlbumPickerAppearance,
    private val imageLoader: AlbumImageLoader,
    private val scope: CoroutineScope,
    initialItems: List<AlbumMedia>,
    initialIndex: Int,
    private var nextOffset: Int?,
    private val loadMore: suspend (offset: Int, limit: Int) -> Result<List<AlbumMedia>>,
) {
    private val dialog = Dialog(activity, R.style.auv_theme_album_picker_preview)
    private val adapter = PreviewAdapter(appearance, imageLoader, initialItems)
    private var loadJob: Job? = null
    private var endReached = nextOffset == null

    init {
        dialog.setContentView(R.layout.auv_dialog_album_preview)
        val pager = dialog.findViewById<ViewPager2>(R.id.auv_preview_pager)
        pager.adapter = adapter
        pager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    maybeLoadMore(position)
                }
            },
        )
        dialog.setOnDismissListener {
            loadJob?.cancel()
            pager.adapter = null
        }
        dialog.show()
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(
                (appearance.previewBackgroundColor
                    ?: activity.getColorCompat(android.R.color.black)).toDrawable(),
            )
        }
        val targetIndex = initialIndex.coerceIn(0, (adapter.itemCount - 1).coerceAtLeast(0))
        pager.setCurrentItem(targetIndex, false)
        pager.post { maybeLoadMore(targetIndex) }
    }

    private fun maybeLoadMore(position: Int) {
        val offset = nextOffset ?: return
        if (endReached || loadJob?.isActive == true) return
        if (position < adapter.itemCount - PREVIEW_PREFETCH_DISTANCE) return

        loadJob = scope.launch {
            loadMore(offset, PREVIEW_PAGE_SIZE)
                .onSuccess { page ->
                    nextOffset = offset + page.size
                    if (page.size < PREVIEW_PAGE_SIZE) endReached = true
                    adapter.append(page)
                }
                .onFailure { failure ->
                    Toast.makeText(
                        activity.applicationContext,
                        failure.message ?: activity.getString(R.string.auv_preview_load_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
        }
    }

    private class PreviewAdapter(
        private val appearance: AlbumPickerAppearance,
        private val imageLoader: AlbumImageLoader,
        initialItems: List<AlbumMedia>,
    ) : RecyclerView.Adapter<PreviewHolder>() {
        private val items = initialItems.distinctBy { it.uri }.toMutableList()
        private val uris = items.mapTo(linkedSetOf()) { it.uri }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewHolder =
            PreviewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.auv_item_album_preview_page, parent, false),
                appearance,
                imageLoader,
            )

        override fun onBindViewHolder(holder: PreviewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun onViewRecycled(holder: PreviewHolder) {
            holder.clear()
        }

        override fun getItemCount(): Int = items.size

        fun append(page: List<AlbumMedia>) {
            val additions = page.filter { uris.add(it.uri) }
            if (additions.isEmpty()) return
            val start = items.size
            items += additions
            notifyItemRangeInserted(start, additions.size)
        }
    }

    private class PreviewHolder(
        itemView: View,
        private val appearance: AlbumPickerAppearance,
        private val imageLoader: AlbumImageLoader,
    ) : RecyclerView.ViewHolder(itemView) {
        private val zoomImage: ZoomImageView = itemView.findViewById(R.id.auv_preview_zoom_image)
        private val videoCover: ImageView = itemView.findViewById(R.id.auv_preview_video_cover)
        private val play: ImageView = itemView.findViewById(R.id.auv_preview_video_play)

        fun bind(media: AlbumMedia) {
            clear()
            val background = appearance.previewBackgroundColor
                ?: itemView.context.getColorCompat(android.R.color.black)
            itemView.setBackgroundColor(background)
            if (media.mediaType == AlbumMediaType.IMAGE) {
                zoomImage.visibility = View.VISIBLE
                videoCover.visibility = View.GONE
                play.visibility = View.GONE
                imageLoader.load(zoomImage, media, AlbumImageTarget.PREVIEW_IMAGE)
            } else {
                zoomImage.visibility = View.GONE
                videoCover.visibility = View.VISIBLE
                play.visibility = View.VISIBLE
                imageLoader.load(videoCover, media, AlbumImageTarget.VIDEO_COVER)
                play.setImageResource(appearance.videoIconRes ?: R.drawable.auv_ic_album_play)
                play.setOnClickListener { /* 视频预览仅展示封面，不在选择器内播放。 */ }
            }
        }

        fun clear() {
            imageLoader.clear(zoomImage)
            imageLoader.clear(videoCover)
            play.setOnClickListener(null)
        }
    }

    private companion object {
        const val PREVIEW_PAGE_SIZE = 30
        const val PREVIEW_PREFETCH_DISTANCE = 3
    }
}

private fun android.content.Context.getColorCompat(resourceId: Int): Int =
    ContextCompat.getColor(this, resourceId)
