package com.github.sceneren.album.api

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.map
import com.github.sceneren.album.api.internal.database.AlbumDatabaseFactory
import com.github.sceneren.album.api.internal.database.PickedMediaDraft
import com.github.sceneren.album.api.internal.database.PickedMediaEntity
import com.github.sceneren.album.api.internal.database.PickedMediaStore
import com.github.sceneren.album.api.internal.database.RoomPickedMediaStore
import com.github.sceneren.album.api.internal.database.toAlbumMedia
import com.github.sceneren.album.api.internal.mediastore.AndroidMediaStoreDataSource
import com.github.sceneren.album.api.internal.mediastore.MediaStoreDataSource
import com.github.sceneren.album.api.internal.mediastore.MediaStoreMediaPagingSource
import com.github.sceneren.album.api.internal.permission.AndroidMediaAccessResolver
import com.github.sceneren.album.api.internal.permission.MediaAccessResolver
import com.github.sceneren.album.api.internal.picker.AndroidPersistableGrantManager
import com.github.sceneren.album.api.internal.picker.ContentResolverUriAccessChecker
import com.github.sceneren.album.api.internal.picker.ContentResolverUriMetadataReader
import com.github.sceneren.album.api.internal.picker.PersistableGrantManager
import com.github.sceneren.album.api.internal.picker.PhotoPickerRegistrar
import com.github.sceneren.album.api.internal.picker.PhotoPickerResultProcessor
import com.github.sceneren.album.api.internal.picker.PickerRegistrar
import com.github.sceneren.album.api.internal.picker.UriAccessChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Data-only entry point for permission-aware media paging and persistent Photo Picker selections.
 *
 * Hosts own permission requests and UI. A full media permission routes reads to MediaStore;
 * partial or denied access routes reads to the library's persisted Photo Picker database.
 */
class AlbumApi internal constructor(
    private val accessResolver: MediaAccessResolver,
    private val mediaStore: MediaStoreDataSource,
    private val pickedStore: PickedMediaStore,
    private val pickerRegistrar: PickerRegistrar,
    private val grantManager: PersistableGrantManager,
    private val uriAccessChecker: UriAccessChecker,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Returns the effective media-library access for [mediaFilter]. */
    fun getMediaAccessStatus(
        mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
    ): MediaAccessStatus = accessResolver.resolve(mediaFilter)

    /**
     * Persists media currently visible under PARTIAL system access for [mediaFilter].
     *
     * The records do not own persistable URI grants because their access is controlled by the
     * system selected-media permission. FULL and DENIED access return zero without querying
     * MediaStore.
     */
    suspend fun syncPartialSelections(
        mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
    ): Result<Int> = resultOnIo {
        if (accessResolver.resolve(mediaFilter) != MediaAccessStatus.PARTIAL) {
            return@resultOnIo 0
        }

        val selectedAtEpochMillis = System.currentTimeMillis()
        val drafts = mediaStore.loadAll(mediaFilter).map { media ->
            PickedMediaDraft(
                uri = media.uri.toString(),
                mediaType = media.mediaType.name,
                displayName = media.displayName,
                mimeType = media.mimeType,
                sizeBytes = media.sizeBytes,
                width = media.width,
                height = media.height,
                durationMillis = media.durationMillis,
                selectedAtEpochMillis = selectedAtEpochMillis,
                ownsPersistableGrant = false,
            )
        }
        pickedStore.upsertBatch(drafts)
        drafts.size
    }

    /**
     * Creates a cold paged media feed for [mediaFilter] and, for MediaStore, [bucketId].
     */
    fun getMediaFeed(
        mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
        bucketId: Long = AlbumDirectory.ALL_BUCKET_ID,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): AlbumMediaFeed {
        require(pageSize > 0) { "pageSize must be > 0" }
        val status = accessResolver.resolve(mediaFilter)
        val config = PagingConfig(
            pageSize = pageSize,
            enablePlaceholders = false,
        )
        val source = if (status == MediaAccessStatus.FULL) {
            AlbumMediaSource.MEDIA_STORE
        } else {
            AlbumMediaSource.PHOTO_PICKER
        }
        val flow = when (source) {
            AlbumMediaSource.MEDIA_STORE -> Pager(config) {
                MediaStoreMediaPagingSource(
                    dataSource = mediaStore,
                    mediaFilter = mediaFilter,
                    bucketId = bucketId,
                )
            }.flow

            else -> Pager(config) {
                pickedStore.pagingSource(mediaFilter)
            }.flow.map { pagingData ->
                pagingData.map(PickedMediaEntity::toAlbumMedia)
            }
        }
        return AlbumMediaFeed(
            mediaFilter = mediaFilter,
            source = source,
            accessStatus = status,
            pagingData = flow,
        )
    }

    /**
     * 按确定的偏移量加载一页媒体，供预览页在用户左右滑动时继续按需取数。
     *
     * 完全授权时读取 MediaStore，并应用 [bucketId]；部分授权或未授权时读取已经持久化
     * 的 Photo Picker 列表。该接口不会一次性把整个相册加载到内存。
     */
    suspend fun loadMediaPage(
        mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
        bucketId: Long = AlbumDirectory.ALL_BUCKET_ID,
        offset: Int = 0,
        limit: Int = DEFAULT_PAGE_SIZE,
    ): Result<List<AlbumMedia>> = resultOnIo {
        require(offset >= 0) { "offset 不能小于 0" }
        require(limit > 0) { "limit 必须大于 0" }

        if (accessResolver.resolve(mediaFilter) == MediaAccessStatus.FULL) {
            mediaStore.loadPage(mediaFilter, bucketId, offset, limit)
        } else {
            pickedStore.loadPage(mediaFilter, offset, limit).map(PickedMediaEntity::toAlbumMedia)
        }
    }

    /** Returns MediaStore directories when access is full, otherwise an empty list. */
    suspend fun getMediaDirectories(
        mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
    ): Result<List<AlbumDirectory>> = resultOnIo {
        if (accessResolver.resolve(mediaFilter) == MediaAccessStatus.FULL) {
            mediaStore.getDirectories(mediaFilter)
        } else {
            emptyList()
        }
    }

    /**
     * Registers a lifecycle-bound Photo Picker launcher before [activity] is started.
     *
     * A null [maxSelectionCount] applies no library-defined cap; the platform picker may still
     * impose its own limit. Successful selections are persisted for later paging.
     */
    fun registerPhotoPicker(
        activity: ComponentActivity,
        mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
        maxSelectionCount: Int? = null,
        onResult: (PhotoPickResult) -> Unit,
    ): AlbumPhotoPickerLauncher = pickerRegistrar.register(
        activity = activity,
        mediaFilter = mediaFilter,
        maxSelectionCount = maxSelectionCount,
        onResult = onResult,
    )

    /** Removes one persisted picker selection and releases a grant owned by this library. */
    suspend fun removePersistedSelection(uri: Uri): Result<Boolean> = resultOnIo {
        val removed = pickedStore.remove(uri.toString()) ?: return@resultOnIo false
        if (removed.ownsPersistableGrant) {
            grantManager.releaseRead(uri)
        }
        true
    }

    /** Clears persisted picker selections and releases all grants owned by this library. */
    suspend fun clearPersistedSelections(): Result<Int> = resultOnIo {
        val removed = pickedStore.clear()
        var firstFailure: Exception? = null
        removed.forEach { item ->
            if (item.ownsPersistableGrant) {
                try {
                    grantManager.releaseRead(item.uri.toUri())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (exception: Exception) {
                    if (firstFailure == null) firstFailure = exception
                }
            }
        }
        firstFailure?.let { throw it }
        removed.size
    }

    /** Removes picker records whose persisted URI access is no longer usable. */
    suspend fun reconcilePersistedSelections(): Result<Int> = resultOnIo {
        reconcilePersistedSelectionsInternal()
    }

    private suspend fun reconcilePersistedSelectionsInternal(): Int {
        val persistedUris = grantManager.persistedReadUris()
        var removedCount = 0
        pickedStore.all().forEach { item ->
            val uri = item.uri.toUri()
            val hasPersistedGrant = uri in persistedUris
            val stale = !uriAccessChecker.canRead(uri) ||
                (item.ownsPersistableGrant && !hasPersistedGrant)
            if (stale) {
                val removed = pickedStore.remove(item.uri) ?: return@forEach
                if (removed.ownsPersistableGrant && hasPersistedGrant) {
                    grantManager.releaseRead(uri)
                }
                removedCount++
            }
        }
        return removedCount
    }

    /** 创建由 View/Compose 相册页面共用的选择会话客户端。 */
    fun createPickerClient(context: Context): AlbumPickerClient =
        AlbumPickerClient(
            context = context.applicationContext,
            pickedStore = pickedStore,
        )

    /** 删除本库生成且位于应用专属目录中的文件。 */
    suspend fun deleteGeneratedMedia(context: Context, filePath: String): Result<Boolean> =
        resultOnIo {
            val file = generatedFile(context, filePath)
            if (file == null) {
                false
            } else {
                val deleted = !file.exists() || file.delete()
                if (deleted) reconcilePersistedSelectionsInternal()
                deleted
            }
        }

    /** 清理应用专属相册复制、压缩和相机文件，原始媒体不会被删除。 */
    suspend fun clearGeneratedMedia(context: Context): Result<Int> = resultOnIo {
        val root = context.applicationContext.getExternalFilesDir(null)
            ?: return@resultOnIo 0
        val directories = listOf("photo_picker", "luban", "camera")
            .map { java.io.File(root, it) }
        val deletedCount = directories.sumOf { directory ->
            directory.walkTopDown()
                .filter { it.isFile }
                .onEach { it.delete() }
                .count()
        }
        reconcilePersistedSelectionsInternal()
        deletedCount
    }

    private fun generatedFile(context: Context, path: String): java.io.File? {
        val root = context.applicationContext.getExternalFilesDir(null) ?: return null
        val candidate = java.io.File(path).canonicalFile
        val allowedRoots = listOf("photo_picker", "luban", "camera")
            .map { java.io.File(root, it).canonicalFile }
        return candidate.takeIf { file ->
            allowedRoots.any { allowed ->
                file.path == allowed.path || file.path.startsWith(allowed.path + java.io.File.separator)
            }
        }
    }

    private suspend fun <T> resultOnIo(block: suspend () -> T): Result<T> = try {
        Result.success(withContext(ioDispatcher) { block() })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    companion object {
        /** Default number of items requested by each paging load. */
        const val DEFAULT_PAGE_SIZE: Int = 50

        /** Creates an application-scoped API instance backed by MediaStore and Room. */
        fun create(context: Context): AlbumApi {
            val appContext = context.applicationContext
            val resolver = appContext.contentResolver
            val pickedStore = RoomPickedMediaStore(AlbumDatabaseFactory.get(appContext))
            val grantManager = AndroidPersistableGrantManager(resolver)
            val processor = PhotoPickerResultProcessor(
                grantManager = grantManager,
                metadataReader = ContentResolverUriMetadataReader(resolver),
                store = pickedStore,
            )
            val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            return AlbumApi(
                accessResolver = AndroidMediaAccessResolver(appContext),
                mediaStore = AndroidMediaStoreDataSource(appContext),
                pickedStore = pickedStore,
                pickerRegistrar = PhotoPickerRegistrar(processor, applicationScope),
                grantManager = grantManager,
                uriAccessChecker = ContentResolverUriAccessChecker(resolver),
            )
        }
    }
}
