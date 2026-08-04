package com.github.sceneren.album.api.internal.permission

internal data class MediaPermissionSnapshot(
    val sdkInt: Int,
    val readExternalStorage: Boolean,
    val readMediaImages: Boolean,
    val readMediaVideo: Boolean,
    val readVisualUserSelected: Boolean,
)
