package com.github.sceneren.album.ui.view

import android.content.Context
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
