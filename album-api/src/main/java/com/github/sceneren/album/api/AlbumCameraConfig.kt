package com.github.sceneren.album.api

/** 相机入口和混合媒体模式下的拍摄类型。 */
data class AlbumCameraConfig(
    val enabled: Boolean = true,
    val mixedMediaCaptureType: AlbumCameraCaptureType = AlbumCameraCaptureType.PHOTO,
)
