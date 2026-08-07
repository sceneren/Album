package com.github.sceneren.album.ui.view

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaSpecialFormat
import com.github.sceneren.album.api.AlbumMediaType
import java.util.Locale

/** 持有并绑定 `MediaHolder` 对应的列表项视图。 */
internal class MediaHolder(
    itemView: View,
    private val appearance: AlbumPickerAppearance,
    private val imageLoader: AlbumImageLoader,
    cellSize: Int,
) : RecyclerView.ViewHolder(itemView) {
    private val image: ImageView = itemView.findViewById(R.id.auv_media_image)
    private val scrim: View = itemView.findViewById(R.id.auv_media_scrim)
    private val check: ImageView = itemView.findViewById(R.id.auv_media_check)
    private val videoInfo: TextView = itemView.findViewById(R.id.auv_media_video_info)
    private val badge: TextView = itemView.findViewById(R.id.auv_media_badge)
    private var boundMedia: AlbumMedia? = null

    init {
        itemView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            cellSize,
        )
    }

    /** 执行 `bind` 方法定义的处理。 */
    fun bind(
        media: AlbumMedia,
        selected: Boolean,
        selectionBlocked: Boolean,
        onPreview: (AlbumMedia) -> Unit,
        onToggle: (AlbumMedia) -> Unit,
    ) {
        if (boundMedia != media) {
            imageLoader.clear(image)
            imageLoader.load(image, media, AlbumImageTarget.GRID_THUMBNAIL)
            bindMediaInfo(media)
            boundMedia = media
        }
        updateSelectionState(media, selected, selectionBlocked, onPreview, onToggle)
    }

    /** 更新 `updateSelectionState` 对应的状态。 */
    fun updateSelectionState(
        media: AlbumMedia,
        selected: Boolean,
        selectionBlocked: Boolean,
        onPreview: (AlbumMedia) -> Unit,
        onToggle: (AlbumMedia) -> Unit,
    ) {
        val checkIcon = if (selected) {
            appearance.checkedIconRes ?: R.drawable.auv_ic_album_checked
        } else {
            appearance.uncheckedIconRes ?: R.drawable.auv_ic_album_unchecked
        }
        check.setImageResource(checkIcon)
        check.setBackgroundColor(Color.TRANSPARENT)
        check.setOnClickListener { onToggle(media) }
        scrim.isVisible = selected || selectionBlocked
        scrim.setBackgroundColor(
            when {
                selected -> appearance.scrimColor
                    ?: itemView.context.color(R.color.auv_media_selected_scrim)
                selectionBlocked -> itemView.context.color(R.color.auv_media_blocked_scrim)
                else -> Color.TRANSPARENT
            },
        )
        scrim.isClickable = selected || selectionBlocked
        scrim.setOnClickListener(
            when {
                selected -> View.OnClickListener { onPreview(media) }
                selectionBlocked -> View.OnClickListener { }
                else -> null
            },
        )
        itemView.setOnClickListener(
            if (selectionBlocked) null else View.OnClickListener { onPreview(media) },
        )
    }

    /** Binds lightweight media metadata without opening or decoding the source file. */
    private fun bindMediaInfo(media: AlbumMedia) {
        val duration = media.videoDurationLabel()
        videoInfo.isVisible = duration != null
        videoInfo.text = duration
        videoInfo.contentDescription = duration?.let {
            itemView.context.getString(R.string.auv_video_duration, it)
        }

        val badgeText = when (media.gridBadge()) {
            MediaGridBadge.GIF -> itemView.context.getString(R.string.auv_media_badge_gif)
            MediaGridBadge.LONG_IMAGE -> {
                itemView.context.getString(R.string.auv_media_badge_long_image)
            }
            MediaGridBadge.LIVE_PHOTO -> {
                itemView.context.getString(R.string.auv_media_badge_live_photo)
            }
            null -> null
        }
        badge.isVisible = badgeText != null
        badge.text = badgeText
    }

    /** 清理 `clear` 对应的数据或资源。 */
    fun clear() {
        boundMedia = null
        imageLoader.clear(image)
        videoInfo.isVisible = false
        videoInfo.text = null
        videoInfo.contentDescription = null
        badge.isVisible = false
        badge.text = null
        scrim.isVisible = false
        scrim.setBackgroundColor(Color.TRANSPARENT)
        scrim.isClickable = false
        scrim.setOnClickListener(null)
        check.setImageDrawable(null)
        check.setBackgroundColor(Color.TRANSPARENT)
        check.setOnClickListener(null)
        itemView.setOnClickListener(null)
    }
}

/** Labels supported by a media tile in the picker grid. */
internal enum class MediaGridBadge {
    GIF,
    LONG_IMAGE,
    LIVE_PHOTO,
}

/** Returns the highest-priority badge for an image tile. */
internal fun AlbumMedia.gridBadge(): MediaGridBadge? {
    if (mediaType != AlbumMediaType.IMAGE) return null
    if (specialFormat == AlbumMediaSpecialFormat.MOTION_PHOTO) {
        return MediaGridBadge.LIVE_PHOTO
    }
    if (
        specialFormat == AlbumMediaSpecialFormat.GIF ||
        mimeType.equals("image/gif", ignoreCase = true) ||
        displayName?.endsWith(".gif", ignoreCase = true) == true
    ) {
        return MediaGridBadge.GIF
    }
    val imageWidth = width?.takeIf { it > 0 } ?: return null
    val imageHeight = height?.takeIf { it > 0 } ?: return null
    return if (imageHeight.toLong() >= imageWidth.toLong() * LONG_IMAGE_RATIO) {
        MediaGridBadge.LONG_IMAGE
    } else {
        null
    }
}

/** Formats video duration as `mm:ss`, or `h:mm:ss` for long videos. */
internal fun AlbumMedia.videoDurationLabel(): String? {
    if (mediaType != AlbumMediaType.VIDEO) return null
    val totalSeconds = durationMillis?.takeIf { it >= 0L }?.div(MILLIS_PER_SECOND)
        ?: return UNKNOWN_VIDEO_DURATION
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

private const val LONG_IMAGE_RATIO = 3L
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val UNKNOWN_VIDEO_DURATION = "--:--"
