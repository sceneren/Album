package com.github.sceneren.album.api

/** 由宿主控制、通过 [AlbumApi.registerPhotoPicker] 注册的选择器启动器。 */
interface AlbumPhotoPickerLauncher {
    val mediaFilter: AlbumMediaFilter

    /** 执行 `launch` 方法定义的处理。 */
    fun launch()
}
