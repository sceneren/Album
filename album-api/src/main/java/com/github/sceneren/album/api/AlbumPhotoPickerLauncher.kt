package com.github.sceneren.album.api

/** Host-controlled launcher registered through [AlbumApi.registerPhotoPicker]. */
interface AlbumPhotoPickerLauncher {
    val mediaFilter: AlbumMediaFilter

    fun launch()
}
