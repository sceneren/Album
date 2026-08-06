package com.github.sceneren.album.api.internal.permission

/** 描述 `MediaPermissionSnapshot` 数据。 */
internal data class MediaPermissionSnapshot(
    /** 表示 `sdkInt` 对应的数据。 */
    val sdkInt: Int,
    /** 表示 `readExternalStorage` 对应的数据。 */
    val readExternalStorage: Boolean,
    /** 表示 `readMediaImages` 对应的数据。 */
    val readMediaImages: Boolean,
    /** 表示 `readMediaVideo` 对应的数据。 */
    val readMediaVideo: Boolean,
    /** 表示 `readVisualUserSelected` 对应的数据。 */
    val readVisualUserSelected: Boolean,
)
