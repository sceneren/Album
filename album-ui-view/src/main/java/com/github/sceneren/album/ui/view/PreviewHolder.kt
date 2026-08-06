package com.github.sceneren.album.ui.view

import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.github.panpf.zoomimage.ZoomImageView
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaType

/** 持有并绑定 `PreviewHolder` 对应的列表项视图。 */
internal class PreviewHolder(
    itemView: View,
    private val appearance: AlbumPickerAppearance,
    private val imageLoader: AlbumImageLoader,
) : RecyclerView.ViewHolder(itemView) {
    private val zoomImage: ZoomImageView = itemView.findViewById(R.id.auv_preview_zoom_image)
    private val videoCover: ImageView = itemView.findViewById(R.id.auv_preview_video_cover)
    private val play: ImageView = itemView.findViewById(R.id.auv_preview_video_play)

    /** 执行 `bind` 方法定义的处理。 */
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

    /** 清理 `clear` 对应的数据或资源。 */
    fun clear() {
        imageLoader.clear(zoomImage)
        imageLoader.clear(videoCover)
        play.setOnClickListener(null)
    }
}
