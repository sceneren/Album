package com.github.sceneren.album.ui.view

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.github.panpf.zoomimage.ZoomImageView
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.ui.view.internal.video.PreviewVideoOutput
import com.github.sceneren.album.video.AlbumVideoPlayer

/** Keeps image loading unchanged and accepts an optional engine-owned video rendering View. */
internal class PreviewHolder(
    itemView: View,
    private val appearance: AlbumPickerAppearance,
    private val imageLoader: AlbumImageLoader,
    private val videoPlaybackEnabled: Boolean = false,
) : RecyclerView.ViewHolder(itemView), PreviewVideoOutput {
    private val zoomImage: ZoomImageView = itemView.findViewById(R.id.auv_preview_zoom_image)
    private val videoCover: ImageView = itemView.findViewById(R.id.auv_preview_video_cover)
    private val videoSurface: FrameLayout = itemView.findViewById(R.id.auv_preview_video_surface)
    private val staticPlay: ImageView = itemView.findViewById(R.id.auv_preview_video_static_play)
    private var isVideo = false
    var boundMedia: AlbumMedia? = null
        private set

    fun bind(media: AlbumMedia) {
        clear()
        boundMedia = media
        isVideo = media.mediaType == AlbumMediaType.VIDEO
        val background = appearance.previewBackgroundColor
            ?: itemView.context.getColorCompat(android.R.color.black)
        itemView.setBackgroundColor(background)
        if (!isVideo) {
            zoomImage.visibility = View.VISIBLE
            videoCover.visibility = View.GONE
            staticPlay.visibility = View.GONE
            zoomImage.scaleType = ImageView.ScaleType.FIT_CENTER
            imageLoader.loadPreview(zoomImage, media)
        } else {
            zoomImage.visibility = View.GONE
            videoCover.visibility = View.VISIBLE
            staticPlay.visibility = if (videoPlaybackEnabled) View.GONE else View.VISIBLE
            staticPlay.setImageResource(appearance.videoIconRes ?: R.drawable.auv_ic_album_play)
            imageLoader.load(videoCover, media, AlbumImageTarget.VIDEO_COVER)
        }
    }

    override fun attach(player: AlbumVideoPlayer?) {
        videoSurface.removeAllViews()
        videoSurface.visibility = if (player == null) View.GONE else View.VISIBLE
        if (player != null) {
            videoSurface.addView(
                player.view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    override fun showFrame(hasRenderedFrame: Boolean) {
        videoCover.visibility = if (isVideo && !hasRenderedFrame) View.VISIBLE else View.GONE
    }

    fun clear() {
        attach(null)
        imageLoader.clear(zoomImage)
        imageLoader.clear(videoCover)
        boundMedia = null
        isVideo = false
        staticPlay.visibility = View.GONE
    }
}
