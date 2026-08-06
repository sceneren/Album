package com.github.sceneren.album.api

import android.net.Uri

/** 待交给系统相机 Activity Result Contract 的输出文件。 */
data class AlbumCameraCapture(
    val uri: Uri,
    val filePath: String,
    val mediaType: AlbumMediaType,
)
