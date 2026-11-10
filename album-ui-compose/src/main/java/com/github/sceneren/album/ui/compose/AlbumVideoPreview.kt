package com.github.sceneren.album.ui.compose

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.ui.compose.internal.video.VideoPreviewSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** A rendering surface only; the session, not a prefetched pager item, owns playback. */
@Composable
internal fun AlbumVideoPreview(
    media: AlbumMedia,
    imageLoader: AlbumImageLoader,
    session: VideoPreviewSession?,
) {
    val player = session?.player
    val hasRenderedFrame = session?.playback?.let { it.hasRenderedFrame && !it.failed } == true
    Box(Modifier.fillMaxSize()) {
        if (player != null) {
            key(player) {
                AndroidView(
                    factory = { context ->
                        FrameLayout(context).apply {
                            addView(
                                player.view,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                ),
                            )
                        }
                    },
                    onRelease = { it.removeAllViews() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (!hasRenderedFrame) {
            Image(
                painter = imageLoader.painter(media, AlbumImageTarget.VIDEO_COVER),
                contentDescription = media.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The reference's three-control capsule, with no extra transport or time labels. */
@Composable
internal fun AlbumVideoControls(
    session: VideoPreviewSession,
    modifier: Modifier = Modifier,
) {
    val playback = session.playback
    val showPause = playback.playWhenReady && !playback.ended && !playback.failed
    val foreground = colorResource(R.color.auc_video_controls_foreground)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (playback.failed) {
            Text(
                text = stringResource(R.string.auc_video_play_failed),
                color = foreground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = dimensionResource(R.dimen.auc_toolbar_padding_horizontal)),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(dimensionResource(R.dimen.auc_video_controls_height))
                .background(colorResource(R.color.auc_video_controls_background), CircleShape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = session::togglePlayback,
                enabled = session.player != null,
                modifier = Modifier.size(dimensionResource(R.dimen.auc_video_controls_height)),
            ) {
                Icon(
                    painterResource(if (showPause) R.drawable.auc_ic_video_pause else R.drawable.auc_ic_video_play),
                    contentDescription = stringResource(if (showPause) R.string.auc_video_pause else R.string.auc_video_play),
                    tint = Color.Unspecified,
                    modifier = Modifier.size(dimensionResource(R.dimen.auc_video_controls_icon_size)),
                )
            }
            // Rebuild listeners and drag state when a session/foreground engine is replaced.
            key(session, session.player) { VideoProgress(session, Modifier.weight(1f)) }
            IconButton(
                onClick = session::toggleMute,
                enabled = session.player != null,
                modifier = Modifier.size(dimensionResource(R.dimen.auc_video_controls_height)),
            ) {
                Icon(
                    painterResource(if (session.muted) R.drawable.auc_ic_video_muted else R.drawable.auc_ic_video_volume),
                    contentDescription = stringResource(if (session.muted) R.string.auc_video_unmute else R.string.auc_video_mute),
                    tint = Color.Unspecified,
                    modifier = Modifier.size(dimensionResource(R.dimen.auc_video_controls_icon_size)),
                )
            }
        }
    }
}

// Compose owns the AndroidView bounds; the native SeekBar owns accessibility and key seeks.
@SuppressLint("ClickableViewAccessibility", "InflateParams")
@Composable
private fun VideoProgress(session: VideoPreviewSession, modifier: Modifier = Modifier) {
    var dragging by remember(session) { mutableStateOf(false) }
    val playback = session.playback
    // Only this leaf polls position; there is no timer in the grid or for paused/background media.
    LaunchedEffect(session, playback.isPlaying) {
        while (isActive && session.playback.isPlaying) {
            delay(250)
            session.refresh()
        }
    }
    AndroidView(
        factory = { context ->
            (LayoutInflater.from(context).inflate(R.layout.auc_video_progress, null) as SeekBar).apply {
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(seekBar: SeekBar) {
                        dragging = true
                        session.beginScrub()
                    }
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        if (fromUser && !dragging) session.seekToFraction(progress.toFloat() / seekBar.max)
                    }
                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        dragging = false
                        session.endScrub(seekBar.progress.toFloat() / seekBar.max)
                        seekBar.parent.requestDisallowInterceptTouchEvent(false)
                    }
                })
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> view.parent.requestDisallowInterceptTouchEvent(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                            view.parent.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
            }
        },
        update = { seekBar ->
            seekBar.isEnabled = session.player != null && playback.seekable &&
                playback.durationMs > 0 && !playback.failed
            if (!dragging) {
                seekBar.progress = if (playback.durationMs > 0) {
                    (playback.positionMs.toDouble() / playback.durationMs * seekBar.max)
                        .toInt().coerceIn(0, seekBar.max)
                } else {
                    0
                }
            }
        },
        onRelease = {
            dragging = false
            it.setOnSeekBarChangeListener(null)
            it.setOnTouchListener(null)
        },
        modifier = modifier.height(dimensionResource(R.dimen.auc_video_controls_height)),
    )
}
