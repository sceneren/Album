package com.github.sceneren.album.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.ui.graphics.Color
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMedia
import com.github.sceneren.album.api.AlbumMediaSource

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

internal fun Int.toColor() = Color(this)

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
