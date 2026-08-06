package com.github.sceneren.album.api.internal.session

import android.net.Uri
import com.github.sceneren.album.api.AlbumMediaType

/** 描述尚未返回结果的相机请求所预留的输出。 */
internal data class AlbumPendingCameraCapture(
    /** 媒体内容 URI。 */
    val uri: Uri,
    /** 文件的绝对路径。 */
    val filePath: String,
    /** 媒体类型。 */
    val mediaType: AlbumMediaType,
)
