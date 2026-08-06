package com.github.sceneren.album.api.internal.mediastore

import android.net.Uri
import com.github.sceneren.album.api.AlbumDirectory
import com.github.sceneren.album.api.AlbumMediaType

/** 在流式读取 MediaStore 目录记录时使用的可变累加器。 */
internal data class MutableDirectory(
    /** 媒体目录标识。 */
    val bucketId: Long,
    /** 媒体目录名称。 */
    val bucketName: String?,
    /** 目录封面媒体的 URI。 */
    val coverUri: Uri,
    /** 目录封面媒体的类型。 */
    val coverMediaType: AlbumMediaType,
    /** 表示 `firstMediaDate` 对应的数据。 */
    val firstMediaDate: Long,
    /** 目录包含的媒体数量。 */
    var mediaCount: Long = 0,
) {
    /** 将当前对象转换为 `toAlbumDirectory` 对应的结果。 */
    fun toAlbumDirectory() = AlbumDirectory(
        bucketId = bucketId,
        bucketName = bucketName,
        coverUri = coverUri,
        coverMediaType = coverMediaType,
        mediaCount = mediaCount,
    )
}
