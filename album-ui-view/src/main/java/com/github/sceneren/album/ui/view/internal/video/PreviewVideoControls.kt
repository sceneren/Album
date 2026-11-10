package com.github.sceneren.album.ui.view.internal.video

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import com.github.sceneren.album.ui.view.R

/** Binds the accessible 48dp controls; a single progress callback runs only while video advances. */
internal class PreviewVideoControls(
    root: View,
    private val playback: PreviewVideoPlayback,
) {
    private val container: View = root.findViewById(R.id.auv_preview_video_controls)
    private val play: ImageButton = root.findViewById(R.id.auv_preview_video_play)
    private val mute: ImageButton = root.findViewById(R.id.auv_preview_video_mute)
    private val progress: SeekBar = root.findViewById(R.id.auv_preview_video_progress)
    private val error: TextView = root.findViewById(R.id.auv_preview_video_error)
    private var dragging = false
    private val updateProgress = Runnable { playback.refresh() }

    init {
        play.setOnClickListener { playback.togglePlay() }
        mute.setOnClickListener { playback.toggleMuted() }
        progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                dragging = true
                playback.beginScrub()
            }

            override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                // D-pad / accessibility seeks have no start/stop tracking callbacks.
                if (fromUser && !dragging) playback.seekToFraction(value.toFloat() / seekBar.max)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                dragging = false
                playback.endScrub(seekBar.progress.toFloat() / seekBar.max)
                seekBar.parent.requestDisallowInterceptTouchEvent(false)
            }
        })
        protectProgressGesture()
    }

    fun render(state: PreviewVideoUiState) {
        container.removeCallbacks(updateProgress)
        if (!state.active) {
            dragging = false
            progress.parent.requestDisallowInterceptTouchEvent(false)
        }
        play.setImageResource(
            if (state.showPause) R.drawable.auv_ic_video_pause else R.drawable.auv_ic_video_play,
        )
        play.contentDescription = container.context.getString(
            when {
                state.failed -> R.string.auv_video_retry
                state.ended -> R.string.auv_video_replay
                state.showPause -> R.string.auv_video_pause
                else -> R.string.auv_video_play
            },
        )
        play.isEnabled = state.active
        mute.setImageResource(
            if (state.muted) R.drawable.auv_ic_video_volume_off else R.drawable.auv_ic_video_volume_on,
        )
        mute.contentDescription = container.context.getString(
            if (state.muted) R.string.auv_video_unmute else R.string.auv_video_mute,
        )
        mute.isEnabled = state.active
        error.visibility = if (state.failed) View.VISIBLE else View.GONE
        progress.isEnabled = state.active && state.seekable && !state.failed
        if (!dragging) {
            progress.progress = if (state.durationMs > 0) {
                (state.positionMs.toDouble() / state.durationMs * progress.max)
                    .toInt().coerceIn(0, progress.max)
            } else {
                0
            }
        }
        if (state.active && state.isPlaying && !dragging) {
            container.postDelayed(updateProgress, PROGRESS_UPDATE_MS)
        }
    }

    fun clear() {
        container.removeCallbacks(updateProgress)
        dragging = false
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun protectProgressGesture() {
        // The platform SeekBar retains its own accessibility and click handling.
        progress.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
    }

    private companion object {
        const val PROGRESS_UPDATE_MS = 250L
    }
}
