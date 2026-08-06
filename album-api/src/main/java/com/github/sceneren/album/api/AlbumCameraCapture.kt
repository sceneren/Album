package com.github.sceneren.album.api

import android.net.Uri

/** 待交给系统相机 Activity Result Contract 的输出文件。 */
data class AlbumCameraCapture(
    /** 媒体内容 URI。 */
    val uri: Uri,
    /** 文件的绝对路径。 */
    val filePath: String,
    /** 媒体类型。 */
    val mediaType: AlbumMediaType,
)
