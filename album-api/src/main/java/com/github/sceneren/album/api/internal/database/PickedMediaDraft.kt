package com.github.sceneren.album.api.internal.database

/** 描述原子写入持久化选择记录前暂存的媒体元数据。 */
internal data class PickedMediaDraft(
    /** 媒体内容 URI。 */
    val uri: String,
    /** 媒体类型。 */
    val mediaType: String,
    /** 媒体展示名称。 */
    val displayName: String?,
    /** 媒体的 MIME 类型。 */
    val mimeType: String?,
    /** 文件大小，单位为字节。 */
    val sizeBytes: Long?,
    /** 媒体像素宽度。 */
    val width: Int?,
    /** 媒体像素高度。 */
    val height: Int?,
    /** 媒体时长，单位为毫秒。 */
    val durationMillis: Long?,
    /** 媒体选择时间，单位为 Unix 毫秒。 */
    val selectedAtEpochMillis: Long,
    /** 本库是否持有该 URI 的可持久化授权。 */
    val ownsPersistableGrant: Boolean,
) {
    /** 将当前对象转换为 `toEntity` 对应的结果。 */
    fun toEntity(
        sortOrder: Long,
        ownsPersistableGrant: Boolean,
    ) = PickedMediaEntity(
        uri = uri,
        mediaType = mediaType,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        durationMillis = durationMillis,
        selectedAtEpochMillis = selectedAtEpochMillis,
        sortOrder = sortOrder,
        ownsPersistableGrant = ownsPersistableGrant,
    )
}
