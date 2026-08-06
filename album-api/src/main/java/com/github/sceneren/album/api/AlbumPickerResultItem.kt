package com.github.sceneren.album.api

import android.net.Uri

/** 一个选择结果。resultUri/filePath 是宿主实际应消费的结果。 */
data class AlbumPickerResultItem(
    val originalUri: Uri,
    val resultUri: Uri,
    val mediaType: AlbumMediaType,
    val compressionStatus: AlbumCompressionStatus,
    /** 复制到应用专属 files/photo_picker 后的原始文件绝对路径。 */
    val originalFilePath: String,
    /** 最终返回文件绝对路径；未压缩时与 originalFilePath 相同。 */
    val filePath: String,
)
