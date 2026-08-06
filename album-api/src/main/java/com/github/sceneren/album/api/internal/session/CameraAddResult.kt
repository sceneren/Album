package com.github.sceneren.album.api.internal.session

/** 封装一次相机媒体插入后生成的选择集合和相机集合。 */
internal data class CameraAddResult(
    /** 当前已选择的媒体列表。 */
    val selected: List<AlbumPickerSelection>,
    /** 当前会话中的相机媒体列表。 */
    val cameraItems: List<AlbumPickerSelection>,
)
