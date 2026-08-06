package com.github.sceneren.album.api.internal.picker

import android.net.Uri
import com.github.sceneren.album.api.AlbumMediaFilter
import com.github.sceneren.album.api.PhotoPickFailure
import com.github.sceneren.album.api.PhotoPickResult
import com.github.sceneren.album.api.internal.database.PickedMediaDraft
import com.github.sceneren.album.api.internal.database.PickedMediaStore
import com.github.sceneren.album.api.internal.database.toAlbumMedia
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 负责 `PhotoPickerResultProcessor` 相关的数据与行为。 */
internal class PhotoPickerResultProcessor(
    private val grantManager: PersistableGrantManager,
    private val metadataReader: UriMetadataReader,
    private val store: PickedMediaStore,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    /** 执行 `process` 方法定义的处理。 */
    suspend fun process(
        uris: List<Uri>,
        mediaFilter: AlbumMediaFilter,
        maxSelectionCount: Int?,
    ): PhotoPickResult = mutex.withLock {
        processLocked(uris, mediaFilter, maxSelectionCount)
    }

    /** 执行 `processLocked` 方法定义的处理。 */
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

    /** 清理 `releaseNewGrants` 对应的数据或资源。 */
    private fun releaseNewGrants(newGrants: List<Uri>) {
        newGrants.asReversed().forEach { uri ->
            try {
                grantManager.releaseRead(uri)
            } catch (_: Exception) {
                // 尽力回滚新增授权，最终仍以原始失败原因作为结果。
            }
        }
    }

}
