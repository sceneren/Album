package com.github.sceneren.album.api.internal.database

/** Metadata staged for an atomic persisted-selection upsert. */
internal data class PickedMediaDraft(
    val uri: String,
    val mediaType: String,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val durationMillis: Long?,
    val selectedAtEpochMillis: Long,
    val ownsPersistableGrant: Boolean,
) {
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
