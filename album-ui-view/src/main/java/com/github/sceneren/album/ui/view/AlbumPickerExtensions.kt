package com.github.sceneren.album.ui.view

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.View
import androidx.core.content.ContextCompat
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaSource
import kotlin.math.roundToInt

internal fun AlbumDirectory.toCoverMedia() = AlbumMedia(
    uri = coverUri,
    mediaType = coverMediaType,
    displayName = bucketName,
    mimeType = null,
    sizeBytes = null,
    dateAddedEpochSeconds = null,
    dateModifiedEpochSeconds = null,
    width = null,
    height = null,
    durationMillis = null,
    bucketId = bucketId,
    bucketName = bucketName,
    selectedAtEpochMillis = null,
    source = AlbumMediaSource.MEDIA_STORE,
)

internal fun View.gridCellSize(metrics: GridMetrics): Int {
    val gridWidth = measuredWidth.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
    val contentWidth = gridWidth.toLong() - paddingLeft - paddingRight
    val totalSpacing = metrics.spacingPx.toLong() * (metrics.spanCount - 1)
    return ((contentWidth - totalSpacing) / metrics.spanCount)
        .coerceAtLeast(1L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
}

internal fun Context.color(resourceId: Int): Int = ContextCompat.getColor(this, resourceId)

internal fun Context.getColorCompat(resourceId: Int): Int =
    ContextCompat.getColor(this, resourceId)

internal fun Context.dpToPx(value: Int): Int =
    (value * resources.displayMetrics.density).roundToInt().coerceAtLeast(0)

internal fun Activity.applyActivityTransitions(animation: AlbumPickerAnimation?) {
    val openEnter = animation?.openEnterResId ?: 0
    val openExit = animation?.openExitResId ?: 0
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, openEnter, openExit)
        overrideActivityTransition(
            Activity.OVERRIDE_TRANSITION_CLOSE,
            animation?.closeEnterResId ?: 0,
            animation?.closeExitResId ?: 0,
        )
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(openEnter, openExit)
    }
}

internal fun Activity.applyLegacyCloseTransition(animation: AlbumPickerAnimation?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    @Suppress("DEPRECATION")
    overridePendingTransition(
        animation?.closeEnterResId ?: 0,
        animation?.closeExitResId ?: 0,
    )
}
