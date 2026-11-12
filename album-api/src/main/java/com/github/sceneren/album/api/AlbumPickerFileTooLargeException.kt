package com.github.sceneren.album.api

import java.io.IOException

/** A selected image/video exceeds the current storage budget, including during a streamed copy. */
class AlbumPickerFileTooLargeException internal constructor() : IOException("选择的文件太大")
