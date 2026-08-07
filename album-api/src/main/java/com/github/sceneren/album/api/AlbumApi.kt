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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 提供支持权限状态的媒体分页与 Photo Picker 持久化选择能力。
 *
 * 宿主负责权限请求和界面展示。完整媒体权限从 MediaStore 读取数据；部分授权或未授权时，
 * 从库内持久化的 Photo Picker 数据库读取数据。
 */
class AlbumApi internal constructor(
    private val accessResolver: MediaAccessResolver,
    private val mediaStore: MediaStoreDataSource,
    private val pickedStore: PickedMediaStore,
    private val pickerRegistrar: PickerRegistrar,
    private val photoPickerResultProcessor: PhotoPickerResultProcessor,
    private val grantManager: PersistableGrantManager,
    private val uriAccessChecker: UriAccessChecker,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** 返回 [mediaFilter] 对应的有效媒体库访问状态。 */
    fun getMediaAccessStatus(
        mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
    ): MediaAccessStatus = accessResolver.resolve(mediaFilter)

    /**
     * 持久化系统在部分授权状态下允许 [mediaFilter] 访问的媒体。
     *
     * 这些记录的访问权由系统“选中的照片和视频”权限控制，因此不持有可持久化 URI 授权。
     * 完整授权和未授权状态直接返回零，不查询 MediaStore。
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
     * 为 [mediaFilter] 创建冷启动分页媒体流；使用 MediaStore 时同时应用 [bucketId]。
     * 持久化选择器数据流会在分页开始前移除不可访问的 URI 记录。
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

            else -> flow {
                withContext(ioDispatcher) {
                    reconcilePersistedSelectionsInternal()
                }
                emitAll(
                    Pager(config) {
                        pickedStore.pagingSource(mediaFilter)
                    }.flow.map { pagingData ->
                        pagingData.map(PickedMediaEntity::toAlbumMedia)
                    },
                )
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

    /** 完整授权时返回 MediaStore 目录，否则返回空列表。 */
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
     * 在 [activity] 启动前注册与其生命周期绑定的 Photo Picker 启动器。
     *
     * [maxSelectionCount] 为 null 时，本库不施加数量上限，但系统选择器仍可能限制数量。
     * 成功选择的媒体会持久化，以供后续分页读取。
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

    /**
     * 验证并持久化由宿主管理的系统 Photo Picker 启动器返回的 URI。
     *
     * 此入口允许非 Activity 界面集成自行注册 Activity Result，同时保持与
     * [registerPhotoPicker] 相同的授权、元数据读取、验证和原子持久化行为。
     */
    suspend fun processPhotoPickerResult(
        uris: List<Uri>,
        mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
        maxSelectionCount: Int? = null,
    ): PhotoPickResult = withContext(ioDispatcher) {
        photoPickerResultProcessor.process(
            uris,
            mediaFilter,
            maxSelectionCount,
        )
    }

    /** 删除一条持久化选择记录，并释放由本库持有的对应授权。 */
    suspend fun removePersistedSelection(uri: Uri): Result<Boolean> = resultOnIo {
        val removed = pickedStore.remove(uri.toString()) ?: return@resultOnIo false
        if (removed.ownsPersistableGrant) {
            grantManager.releaseRead(uri)
        }
        true
    }

    /** 清空持久化选择记录，并释放由本库持有的全部授权。 */
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

    /** 删除持久化 URI 已无法访问的选择器记录。 */
    suspend fun reconcilePersistedSelections(): Result<Int> = resultOnIo {
        reconcilePersistedSelectionsInternal()
    }

    /** 执行 `reconcilePersistedSelectionsInternal` 方法定义的处理。 */
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

    /**
     * 删除本库生成且位于应用专属目录中的文件。
     *
     * `photo_picker` 文件可能由相同源媒体的多个选择结果共享；删除即驱逐该缓存文件。
     */
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

    /** 执行 `generatedFile` 方法定义的处理。 */
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

    /** 执行 `resultOnIo` 方法定义的处理。 */
    private suspend fun <T> resultOnIo(block: suspend () -> T): Result<T> = try {
        Result.success(withContext(ioDispatcher) { block() })
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    /** 提供类级共享常量与工厂能力。 */
    companion object {
        /** 每次分页加载默认请求的条目数。 */
        const val DEFAULT_PAGE_SIZE: Int = 50

        /** 创建由 MediaStore 和 Room 支持的应用级 API 实例。 */
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
                photoPickerResultProcessor = processor,
                grantManager = grantManager,
                uriAccessChecker = ContentResolverUriAccessChecker(resolver),
            )
        }
    }
}
