package com.github.sceneren.album.api.internal.session

/** Selection and camera collections produced by one camera insertion. */
internal data class CameraAddResult(
    val selected: List<AlbumPickerSelection>,
    val cameraItems: List<AlbumPickerSelection>,
)
