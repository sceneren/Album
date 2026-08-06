package com.github.sceneren.album.api

import android.net.Uri

/** 描述用于目录过滤的 MediaStore 媒体桶摘要。 */
data class AlbumDirectory(
    /** 媒体目录标识。 */
    val bucketId: Long,
    /** 媒体目录名称。 */
    val bucketName: String?,
    /** 目录封面媒体的 URI。 */
    val coverUri: Uri,
    /** 目录封面媒体的类型。 */
    val coverMediaType: AlbumMediaType,
    /** 目录包含的媒体数量。 */
    val mediaCount: Long,
) {
    /** 提供类级共享常量与工厂能力。 */
    companion object {
        /** 表示全部匹配媒体的虚拟媒体桶标识。 */
        const val ALL_BUCKET_ID: Long = Long.MIN_VALUE
    }
}
