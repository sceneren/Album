package com.github.sceneren.album.api.internal.picker

import android.net.Uri
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.AlbumMediaType
import com.github.sceneren.album.api.PhotoPickFailure
import com.github.sceneren.album.api.PhotoPickResult
import com.github.sceneren.album.api.internal.database.PickedMediaDraft
import com.github.sceneren.album.api.internal.database.PickedMediaStore
import com.github.sceneren.album.api.internal.database.toAlbumMedia
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PhotoPickerResultProcessor(
    private val grantManager: PersistableGrantManager,
    private val metadataReader: UriMetadataReader,
    private val store: PickedMediaStore,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    suspend fun process(
        uris: List<Uri>,
        mediaFilter: AlbumMediaFilter,
        maxSelectionCount: Int?,
    ): PhotoPickResult = mutex.withLock {
        processLocked(uris, mediaFilter, maxSelectionCount)
    }

    private suspend fun processLocked(
        uris: List<Uri>,
        mediaFilter: AlbumMediaFilter,
        maxSelectionCount: Int?,
    ): PhotoPickResult {
        require(maxSelectionCount == null || maxSelectionCount > 0) {
            "maxSelectionCount must be positive when configured"
        }

        val uniqueUris = LinkedHashSet(uris).toList()
        if (uniqueUris.isEmpty()) return PhotoPickResult.Cancelled
        if (maxSelectionCount != null && uniqueUris.size > maxSelectionCount) {
            return PhotoPickResult.Failed(PhotoPickFailure.SELECTION_LIMIT_EXCEEDED)
        }

        val typedUris = try {
            uniqueUris.map { uri -> uri to metadataReader.requiredType(uri) }
        } catch (exception: Exception) {
            exception.rethrowCancellation()
            return PhotoPickResult.Failed(
                reason = PhotoPickFailure.MEDIA_TYPE_NOT_ALLOWED,
                cause = exception,
            )
        }
        if (typedUris.any { (_, type) -> !mediaFilter.allows(type) }) {
            return PhotoPickResult.Failed(PhotoPickFailure.MEDIA_TYPE_NOT_ALLOWED)
        }

        val persistedBefore = try {
            grantManager.persistedReadUris()
        } catch (exception: Exception) {
            exception.rethrowCancellation()
            return PhotoPickResult.Failed(
                reason = PhotoPickFailure.PERSISTABLE_PERMISSION_FAILED,
                cause = exception,
            )
        }
        val newGrants = mutableListOf<Uri>()

        try {
            typedUris.forEach { (uri, _) ->
                if (uri !in persistedBefore) {
                    grantManager.takeRead(uri)
                    newGrants += uri
                }
            }
        } catch (exception: Exception) {
            exception.rethrowCancellationAfter { releaseNewGrants(newGrants) }
            releaseNewGrants(newGrants)
            return PhotoPickResult.Failed(
                reason = PhotoPickFailure.PERSISTABLE_PERMISSION_FAILED,
                cause = exception,
            )
        }

        val metadata = try {
            typedUris.map { (uri, type) -> metadataReader.read(uri, type) }
        } catch (exception: Exception) {
            exception.rethrowCancellationAfter { releaseNewGrants(newGrants) }
            releaseNewGrants(newGrants)
            return PhotoPickResult.Failed(
                reason = PhotoPickFailure.METADATA_READ_FAILED,
                cause = exception,
            )
        }

        val newlyOwned = newGrants.toSet()
        val selectedAt = clockMillis()
        val drafts = metadata.map { item ->
            PickedMediaDraft(
                uri = item.uri.toString(),
                mediaType = item.mediaType.name,
                displayName = item.displayName,
                mimeType = item.mimeType,
                sizeBytes = item.sizeBytes,
                width = item.width,
                height = item.height,
                durationMillis = item.durationMillis,
                selectedAtEpochMillis = selectedAt,
                ownsPersistableGrant = item.uri in newlyOwned,
            )
        }

        val entities = try {
            store.upsertBatch(drafts)
        } catch (exception: Exception) {
            exception.rethrowCancellationAfter { releaseNewGrants(newGrants) }
            releaseNewGrants(newGrants)
            return PhotoPickResult.Failed(
                reason = PhotoPickFailure.DATABASE_WRITE_FAILED,
                cause = exception,
            )
        }

        return PhotoPickResult.Selected(entities.map { it.toAlbumMedia() })
    }

    private fun releaseNewGrants(newGrants: List<Uri>) {
        newGrants.asReversed().forEach { uri ->
            try {
                grantManager.releaseRead(uri)
            } catch (_: Exception) {
                // Best-effort rollback; the original failure remains authoritative.
            }
        }
    }

    private fun AlbumMediaFilter.allows(mediaType: AlbumMediaType): Boolean = when (this) {
        AlbumMediaFilter.IMAGES -> mediaType == AlbumMediaType.IMAGE
        AlbumMediaFilter.VIDEOS -> mediaType == AlbumMediaType.VIDEO
        AlbumMediaFilter.IMAGES_AND_VIDEOS -> true
    }

    private fun Exception.rethrowCancellation() {
        if (this is CancellationException) throw this
    }

    private inline fun Exception.rethrowCancellationAfter(cleanup: () -> Unit) {
        if (this is CancellationException) {
            cleanup()
            throw this
        }
    }
}
