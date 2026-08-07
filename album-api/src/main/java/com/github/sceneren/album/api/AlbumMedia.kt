package com.github.sceneren.album.api

import android.net.Uri

/** 描述单个图片或视频 URI 的元数据；可空字段表示数据源未提供对应信息。 */
data class AlbumMedia(
    /** 媒体内容 URI。 */
    val uri: Uri,
    /** 媒体类型。 */
    val mediaType: AlbumMediaType,
    /** 媒体展示名称。 */
    val displayName: String?,
    /** 媒体的 MIME 类型。 */
    val mimeType: String?,
    /** 文件大小，单位为字节。 */
    val sizeBytes: Long?,
    /** 媒体添加时间，单位为 Unix 秒。 */
    val dateAddedEpochSeconds: Long?,
    /** 媒体修改时间，单位为 Unix 秒。 */
    val dateModifiedEpochSeconds: Long?,
    /** 媒体像素宽度。 */
    val width: Int?,
    /** 媒体像素高度。 */
    val height: Int?,
    /** 媒体时长，单位为毫秒。 */
    val durationMillis: Long?,
    /** 媒体目录标识。 */
    val bucketId: Long?,
    /** 媒体目录名称。 */
    val bucketName: String?,
    /** 媒体选择时间，单位为 Unix 毫秒。 */
    val selectedAtEpochMillis: Long?,
    /** 媒体数据来源。 */
    val source: AlbumMediaSource,
    /** Special visual format reported by the media provider. */
    val specialFormat: AlbumMediaSpecialFormat = AlbumMediaSpecialFormat.NONE,
)
