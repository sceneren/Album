package com.github.sceneren.album.api

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.map
import com.github.sceneren.album.api.internal.database.AlbumDatabaseFactory
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

class AlbumApi internal constructor(
    private val accessResolver: MediaAccessResolver,
    private val mediaStore: MediaStoreDataSource,
    private val pickedStore: PickedMediaStore,
    private val pickerRegistrar: PickerRegistrar,
    private val grantManager: PersistableGrantManager,
    private val uriAccessChecker: UriAccessChecker,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun getMediaAccessStatus(
        mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
    ): MediaAccessStatus = accessResolver.resolve(mediaFilter)

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

            AlbumMediaSource.PHOTO_PICKER -> Pager(config) {
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

    suspend fun getMediaDirectories(
        mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
    ): Result<List<AlbumDirectory>> = resultOnIo {
        if (accessResolver.resolve(mediaFilter) == MediaAccessStatus.FULL) {
            mediaStore.getDirectories(mediaFilter)
        } else {
            emptyList()
        }
    }

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

    suspend fun removePersistedSelection(uri: Uri): Result<Boolean> = resultOnIo {
        val removed = pickedStore.remove(uri.toString()) ?: return@resultOnIo false
        if (removed.ownsPersistableGrant) {
            grantManager.releaseRead(uri)
        }
        true
    }

    suspend fun clearPersistedSelections(): Result<Int> = resultOnIo {
        val removed = pickedStore.clear()
        removed.forEach { item ->
            if (item.ownsPersistableGrant) {
                grantManager.releaseRead(Uri.parse(item.uri))
            }
        }
        removed.size
    }

    suspend fun reconcilePersistedSelections(): Result<Int> = resultOnIo {
        val persistedUris = grantManager.persistedReadUris()
        var removedCount = 0
        pickedStore.all().forEach { item ->
            val uri = Uri.parse(item.uri)
            val hasPersistedGrant = uri in persistedUris
            val stale = !hasPersistedGrant || !uriAccessChecker.canRead(uri)
            if (stale) {
                val removed = pickedStore.remove(item.uri) ?: return@forEach
                if (removed.ownsPersistableGrant && hasPersistedGrant) {
                    grantManager.releaseRead(uri)
                }
                removedCount++
            }
        }
        removedCount
    }

    private suspend fun <T> resultOnIo(block: suspend () -> T): Result<T> = try {
        Result.success(withContext(ioDispatcher) { block() })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    companion object {
        const val DEFAULT_PAGE_SIZE: Int = 50

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
