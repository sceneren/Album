package com.github.sceneren.album.api

/** 相机入口和混合媒体模式下的拍摄类型。 */
data class AlbumCameraConfig(
    /** 是否启用对应功能。 */
    val enabled: Boolean = true,
    /** 混合媒体模式下使用的拍摄类型。 */
    val mixedMediaCaptureType: AlbumCameraCaptureType = AlbumCameraCaptureType.PHOTO,
)
