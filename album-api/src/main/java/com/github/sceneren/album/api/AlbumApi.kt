package com.github.sceneren.album.api

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.github.sceneren.album.api.internal.mediastore.AndroidMediaStoreDataSource
import com.github.sceneren.album.api.internal.mediastore.AndroidMediaChangeObserver
import com.github.sceneren.album.api.internal.mediastore.MediaStoreDataSource
import com.github.sceneren.album.api.internal.mediastore.MediaStoreMediaPagingSource
import com.github.sceneren.album.api.internal.permission.AndroidMediaAccessResolver
import com.github.sceneren.album.api.internal.permission.MediaAccessResolver
import com.github.sceneren.album.api.internal.picker.AndroidPersistableGrantManager
import com.github.sceneren.album.api.internal.picker.AuthorizedMediaPagingSource
import com.github.sceneren.album.api.internal.picker.ContentResolverUriAccessChecker
import com.github.sceneren.album.api.internal.picker.ContentResolverUriMetadataReader
import com.github.sceneren.album.api.internal.picker.PersistableGrantManager
import com.github.sceneren.album.api.internal.picker.PersistedUriGrant
import com.github.sceneren.album.api.internal.picker.PhotoPickerRegistrar
import com.github.sceneren.album.api.internal.picker.PhotoPickerResultProcessor
import com.github.sceneren.album.api.internal.picker.PickedUriMetadata
import com.github.sceneren.album.api.internal.picker.PickerRegistrar
import com.github.sceneren.album.api.internal.picker.UriAccessChecker
import com.github.sceneren.album.api.internal.picker.UriMetadataReader
import com.github.sceneren.album.api.internal.picker.allows
import com.github.sceneren.album.api.internal.picker.toAlbumMedia
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 提供支持权限状态的媒体分页与系统已授权资源查询能力。
 *
 * 宿主负责权限请求和界面展示。完整媒体权限直接从 MediaStore 分页读取数据；部分授权时结合系统
 * MediaStore 已授权媒体与 `ContentResolver.persistedUriPermissions` 合并分页；未授权时从系统
 * `ContentResolver.persistedUriPermissions` 及应用专属相机目录读取已授权媒体，无需本地 Room 数据库。
 */
class AlbumApi internal constructor(
    private val accessResolver: MediaAccessResolver,
    private val mediaStore: MediaStoreDataSource,
    private val pickerRegistrar: PickerRegistrar,
    private val photoPickerResultProcessor: PhotoPickerResultProcessor,
    private val grantManager: PersistableGrantManager,
    private val metadataReader: UriMetadataReader,
    private val uriAccessChecker: UriAccessChecker,
    private val metadataCache: MutableMap<Uri, PickedUriMetadata> = ConcurrentHashMap(),
    private val cameraMediaLoader: (AlbumMediaFilter) -> List<AlbumMedia> = { emptyList() },
    private val cameraMediaDeleter: (Uri) -> Boolean = { false },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mediaChanges: Flow<Unit> = emptyFlow(),
    private val cacheGeneration: AtomicLong = AtomicLong(),
    private val cameraMediaSignature: (AlbumMediaFilter) -> Any = { cameraMediaLoader(it) },
    private val metadataRevisions: Map<AlbumMediaType, AtomicLong> = AlbumMediaType.entries.associateWith { AtomicLong() },
) {
    private val snapshotMutex = Mutex()
    /**
     * 监听媒体提供方的批量变化；通知和前台恢复后使用 [createMediaChangeTracker] 检查实际变化。
     */
    fun observeMediaChanges(): Flow<Unit> = mediaChanges.flowOn(ioDispatcher)

    /** 为一个列表创建变化基线；过滤无关类型及重复通知，保留未变化文件的元数据缓存。 */
    fun createMediaChangeTracker(
        mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
    ): AlbumMediaChangeTracker = AlbumMediaChangeTracker { readMediaChangeSnapshot(mediaFilter) }

    private suspend fun readMediaChangeSnapshot(
        mediaFilter: AlbumMediaFilter,
        revalidateCachedMedia: Boolean = true,
    ): AlbumMediaChangeTracker.Snapshot =
        withContext(ioDispatcher) {
            snapshotMutex.withLock {
                val status = accessResolver.resolve(mediaFilter)
                val storeSignature = if (status == MediaAccessStatus.DENIED) null
                    else mediaStore.getChangeSignature(mediaFilter)
                val grants = if (status == MediaAccessStatus.FULL) emptyList()
                    else grantManager.persistedReadGrants()
                var cachedMediaChanged = false
                if (status != MediaAccessStatus.FULL && revalidateCachedMedia) {
                    // Only revalidate metadata already used by paging. Never open historical media streams.
                    val grantedUris = grants.mapTo(hashSetOf()) { it.uri }
                    val known = synchronized(metadataCache) { metadataCache.toMap() }
                    for ((uri, cached) in known) {
                        if (uri !in grantedUris || !mediaFilter.allows(cached.mediaType)) continue
                        val read = try {
                            metadataReader.readFresh(uri)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: java.io.FileNotFoundException) {
                            null
                        } catch (_: SecurityException) {
                            null
                        }
                        // A format detected from a header during selection cannot be reprobed here.
                        val fresh = read?.let {
                            if (it.specialFormat == AlbumMediaSpecialFormat.NONE &&
                                cached.copy(specialFormat = AlbumMediaSpecialFormat.NONE) == it) {
                                it.copy(specialFormat = cached.specialFormat)
                            } else it
                        }
                        if (fresh != cached) {
                            metadataReader.invalidateCache(setOf(uri))
                            synchronized(metadataCache) {
                                cacheGeneration.incrementAndGet()
                                metadataCache.remove(uri)
                                if (fresh != null) metadataCache[uri] = fresh
                            }
                            cachedMediaChanged = true
                            metadataRevisions.getValue(cached.mediaType).incrementAndGet()
                            if (fresh != null && fresh.mediaType != cached.mediaType) {
                                metadataRevisions.getValue(fresh.mediaType).incrementAndGet()
                            }
                        }
                    }
                }
                val filteredGrants = if (mediaFilter == AlbumMediaFilter.IMAGES_AND_VIDEOS) grants else grants.filter { grant ->
                    try {
                        mediaFilter.allows(metadataReader.requiredType(grant.uri))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        false
                    }
                }
                AlbumMediaChangeTracker.Snapshot(
                    signature = listOf(status, storeSignature, filteredGrants, cameraMediaSignature(mediaFilter),
                        if (status == MediaAccessStatus.FULL) null else AlbumMediaType.entries
                            .filter { mediaFilter.allows(it) }.map { metadataRevisions.getValue(it).get() }),
                    cachedMediaChanged = cachedMediaChanged,
                )
            }
        }

    /**
     * 使历史元数据失效；接下来的分页会按需校验照片是否仍存在，不逐张打开历史图片内容流。
     * 此方法不释放系统授权，也不清空当前界面的 Paging 快照。
     */
    suspend fun invalidateMediaCache(): Unit = withContext(ioDispatcher) {
        synchronized(metadataCache) {
            cacheGeneration.incrementAndGet()
            metadataReader.invalidateCache()
            metadataCache.clear()
            metadataRevisions.values.forEach { it.incrementAndGet() }
        }
    }

    /** 返回 [mediaFilter] 对应的有效媒体库访问状态。 */
    fun getMediaAccessStatus(
        mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
    ): MediaAccessStatus = accessResolver.resolve(mediaFilter)

    /**
     * 部分授权媒体现由系统 MediaStore 与 `ContentResolver.persistedUriPermissions` 实时按页查询，
     * 不再复制到本地数据库，也不主动逐张打开历史媒体文件描述符。
     */
    suspend fun syncPartialSelections(
        mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
    ): Result<Int> = resultOnIo {
        if (accessResolver.resolve(mediaFilter) != MediaAccessStatus.PARTIAL) {
            return@resultOnIo 0
        }
        0
    }

    /**
     * 为 [mediaFilter] 创建冷启动分页媒体流；使用 MediaStore 时同时应用 [bucketId]。
     * 持久化授权分页仅按当前页读取系统 `ContentResolver` 元数据并复用内存缓存，不逐张打开历史媒体内容流。
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
            initialLoadSize = pageSize,
            enablePlaceholders = false,
        )
        val source = if (status == MediaAccessStatus.FULL) {
            AlbumMediaSource.MEDIA_STORE
        } else {
            AlbumMediaSource.PHOTO_PICKER
        }
        val flow = when (status) {
            MediaAccessStatus.FULL -> Pager(config) {
                MediaStoreMediaPagingSource(
                    dataSource = mediaStore,
                    mediaFilter = mediaFilter,
                    bucketId = bucketId,
                )
            }.flow

            MediaAccessStatus.PARTIAL,
            MediaAccessStatus.DENIED -> Pager(config) {
                // 同一代分页固定授权与相机快照，滚动追加时不反复跨进程读取、排序整个授权列表。
                val grants by lazy { grantManager.persistedReadGrants() }
                val cameraItems by lazy { cameraMediaLoader(mediaFilter) }
                AuthorizedMediaPagingSource(
                    readSignature = { readMediaChangeSnapshot(mediaFilter, revalidateCachedMedia = false).signature },
                ) { offset, limit ->
                    withContext(ioDispatcher) {
                        loadAuthorizedPage(
                            status = status,
                            mediaFilter = mediaFilter,
                            offset = offset,
                            limit = limit,
                            grants = grants,
                            cameraItems = cameraItems,
                        )
                    }
                }
            }.flow
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
     * 完全授权时读取 MediaStore 并应用 [bucketId]；部分授权时合并系统已授权 MediaStore 与持久化 URI；
     * 未授权时读取系统持久化 URI 与相机拍摄记录。该接口不会一次性把整个相册加载到内存。
     */
    suspend fun loadMediaPage(
        mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
        bucketId: Long = AlbumDirectory.ALL_BUCKET_ID,
        offset: Int = 0,
        limit: Int = DEFAULT_PAGE_SIZE,
    ): Result<List<AlbumMedia>> = resultOnIo {
        require(offset >= 0) { "offset 不能小于 0" }
        require(limit > 0) { "limit 必须大于 0" }

        when (val status = accessResolver.resolve(mediaFilter)) {
            MediaAccessStatus.FULL -> mediaStore.loadPage(mediaFilter, bucketId, offset, limit)
            MediaAccessStatus.PARTIAL,
            MediaAccessStatus.DENIED -> loadAuthorizedPage(
                status = status,
                mediaFilter = mediaFilter,
                offset = offset,
                limit = limit,
            )
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
     * 成功选择的媒体通过系统 `ContentResolver.takePersistableUriPermission` 保持持久授权。
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
     * 验证并持久化由宿主管理的系统 Photo Picker 启动器返回的 URI 授权。
     *
     * 此入口允许非 Activity 界面集成自行注册 Activity Result，同时保持与
     * [registerPhotoPicker] 相同的授权、元数据读取和原子校验行为。
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

    /** 释放指定 [uri] 的系统持久化读取授权（或删除对应的已完成相机拍摄文件）。 */
    suspend fun removePersistedSelection(uri: Uri): Result<Boolean> = resultOnIo {
        metadataCache.remove(uri)
        val hasGrant = uri in grantManager.persistedReadUris()
        if (hasGrant) {
            grantManager.releaseRead(uri)
        }
        val deletedCamera = cameraMediaDeleter(uri)
        hasGrant || deletedCamera
    }

    /** 释放当前应用持有的全部系统持久化读取授权。 */
    suspend fun clearPersistedSelections(): Result<Int> = resultOnIo {
        val persistedUris = grantManager.persistedReadUris()
        var firstFailure: Exception? = null
        persistedUris.forEach { uri ->
            metadataCache.remove(uri)
            try {
                grantManager.releaseRead(uri)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                if (firstFailure == null) firstFailure = exception
            }
        }
        firstFailure?.let { throw it }
        persistedUris.size
    }

    /** 检查系统持久化 URI 授权的可读性，并释放已无法访问的失效授权。 */
    suspend fun reconcilePersistedSelections(): Result<Int> = resultOnIo {
        reconcilePersistedSelectionsInternal()
    }

    /** 显式维护时检查持久授权 URI 是否仍可读，释放不可读的失效授权。 */
    private fun reconcilePersistedSelectionsInternal(): Int {
        val persistedUris = grantManager.persistedReadUris()
        var removedCount = 0
        persistedUris.forEach { uri ->
            if (!uriAccessChecker.canRead(uri)) {
                metadataCache.remove(uri)
                grantManager.releaseRead(uri)
                removedCount++
            }
        }
        return removedCount
    }

    /** 按权限状态加载一页系统已授权媒体。 */
    private suspend fun loadAuthorizedPage(
        status: MediaAccessStatus,
        mediaFilter: AlbumMediaFilter,
        offset: Int,
        limit: Int,
        grants: List<PersistedUriGrant> = grantManager.persistedReadGrants(),
        cameraItems: List<AlbumMedia> = cameraMediaLoader(mediaFilter),
    ): List<AlbumMedia> {
        val slice = loadPersistedAndCameraSlice(mediaFilter, offset, limit, grants, cameraItems)
        if (status == MediaAccessStatus.DENIED || slice.items.size >= limit) {
            return slice.items
        }
        if (slice.totalMatchedCount == 0) {
            return mediaStore.loadPage(
                mediaFilter = mediaFilter,
                bucketId = AlbumDirectory.ALL_BUCKET_ID,
                offset = offset,
                limit = limit,
            )
        }
        val extraUris = slice.seenMatchedUris
        // The requested offset is in the DEDUPLICATED feed, not in raw MediaStore rows.
        // Advancing raw offset directly skips/repeats files when persisted URIs also exist there.
        val wantedOffset = (offset - slice.totalMatchedCount).coerceAtLeast(0)
        val remaining = limit - slice.items.size
        val tail = ArrayList<AlbumMedia>(remaining)
        // Locate exclusions with an identity-only cursor; do not reload/decode all preceding pages.
        val excludedPositions = mediaStore.findPositions(
            mediaFilter, AlbumDirectory.ALL_BUCKET_ID, extraUris,
        ).values.sorted()
        var rawOffset = wantedOffset
        for (position in excludedPositions) {
            if (position <= rawOffset) rawOffset++ else break
        }
        val batchSize = maxOf(DEFAULT_PAGE_SIZE, remaining)
        while (tail.size < remaining) {
            val page = mediaStore.loadPage(mediaFilter, AlbumDirectory.ALL_BUCKET_ID, rawOffset, batchSize)
            for (item in page) {
                if (item.uri in extraUris) continue
                tail += item
                if (tail.size == remaining) break
            }
            rawOffset += page.size
            if (page.size < batchSize) break
        }
        return slice.items + tail
    }

    /** 按需惰性读取指定分页窗口 `[offset, offset + limit)` 内的相机与系统持久授权媒体，避免首屏全量查询。 */
    private fun loadPersistedAndCameraSlice(
        mediaFilter: AlbumMediaFilter,
        offset: Int,
        limit: Int,
        grants: List<PersistedUriGrant>,
        cameraItems: List<AlbumMedia>,
    ): PersistedSlice {
        val seenUris = HashSet<Uri>(cameraItems.size + minOf(grants.size, offset + limit))
        val matchedUris = HashSet<Uri>(cameraItems.size + minOf(grants.size, offset + limit))
        val result = ArrayList<AlbumMedia>(limit)
        var matchedIndex = 0

        for (cameraItem in cameraItems) {
            if (!seenUris.add(cameraItem.uri)) continue
            matchedUris += cameraItem.uri
            if (matchedIndex >= offset) {
                result += cameraItem
                if (result.size >= limit) {
                    return PersistedSlice(result, matchedIndex + 1, matchedUris)
                }
            }
            matchedIndex++
        }

        for (grant in grants) {
            if (!seenUris.add(grant.uri)) continue
            val metadata = resolvePersistedGrantMetadata(grant.uri, mediaFilter)
                ?: continue
            matchedUris += grant.uri
            if (matchedIndex >= offset) {
                result += metadata.toAlbumMedia(selectedAtEpochMillis = grant.persistedAtEpochMillis)
                if (result.size >= limit) {
                    return PersistedSlice(result, matchedIndex + 1, matchedUris)
                }
            }
            matchedIndex++
        }

        return PersistedSlice(result, matchedIndex, matchedUris)
    }

    private data class PersistedSlice(
        val items: List<AlbumMedia>,
        val totalMatchedCount: Int,
        val seenMatchedUris: Set<Uri>,
    )

    private fun resolvePersistedGrantMetadata(
        uri: Uri,
        mediaFilter: AlbumMediaFilter,
    ): PickedUriMetadata? {
        val generation = cacheGeneration.get()
        val cached = metadataCache[uri]
        if (cached != null) {
            return if (mediaFilter.allows(cached.mediaType)) {
                cached
            } else {
                null
            }
        }
        val metadata = try {
            val type = metadataReader.requiredType(uri)
            if (!mediaFilter.allows(type)) return null
            metadataReader.read(uri, type, allowStreamProbe = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        }
        synchronized(metadataCache) {
            if (cacheGeneration.get() == generation) metadataCache[uri] = metadata
        }
        return metadata
    }

    /** 创建由 View/Compose 相册页面共用的选择会话客户端。 */
    fun createPickerClient(context: Context): AlbumPickerClient =
        AlbumPickerClient(
            context = context.applicationContext,
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
                !file.exists() || file.delete()
            }
        }

    /** 清理应用专属相册复制、压缩和相机文件，原始媒体不会被删除。 */
    suspend fun clearGeneratedMedia(context: Context): Result<Int> = resultOnIo {
        val root = context.applicationContext.getExternalFilesDir(null)
            ?: return@resultOnIo 0
        val directories = listOf("photo_picker", "luban", "camera")
            .map { java.io.File(root, it) }
        directories.sumOf { directory ->
            directory.walkTopDown()
                .filter { it.isFile }
                .onEach { it.delete() }
                .count()
        }
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

        private val sharedMetadataCache = ConcurrentHashMap<Uri, PickedUriMetadata>()
        private val sharedCacheGeneration = AtomicLong()
        private val sharedMetadataRevisions = AlbumMediaType.entries.associateWith { AtomicLong() }

        /** 创建由系统 MediaStore 与 ContentResolver 授权管理支持的应用级 API 实例。 */
        fun create(context: Context): AlbumApi {
            val appContext = context.applicationContext
            val resolver = appContext.contentResolver
            val grantManager = AndroidPersistableGrantManager(resolver)
            val metadataReader = ContentResolverUriMetadataReader(resolver, sharedMetadataCache, sharedCacheGeneration)
            val processor = PhotoPickerResultProcessor(
                grantManager = grantManager,
                metadataReader = metadataReader,
            )
            val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            return AlbumApi(
                accessResolver = AndroidMediaAccessResolver(appContext),
                mediaStore = AndroidMediaStoreDataSource(appContext),
                pickerRegistrar = PhotoPickerRegistrar(processor, applicationScope),
                photoPickerResultProcessor = processor,
                grantManager = grantManager,
                metadataReader = metadataReader,
                uriAccessChecker = ContentResolverUriAccessChecker(resolver),
                metadataCache = sharedMetadataCache,
                mediaChanges = AndroidMediaChangeObserver(resolver, grantManager).changes(),
                cacheGeneration = sharedCacheGeneration,
                metadataRevisions = sharedMetadataRevisions,
                cameraMediaLoader = { filter ->
                    AlbumPickerClient.loadCompletedCameraMedia(appContext, filter)
                },
                cameraMediaSignature = { filter ->
                    AlbumPickerClient.completedCameraSignature(appContext, filter)
                },
                cameraMediaDeleter = { uri ->
                    AlbumPickerClient.deleteCameraMediaByUri(appContext, uri)
                },
            )
        }
    }
}
