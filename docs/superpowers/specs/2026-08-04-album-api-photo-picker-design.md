# album-api 相册媒体与系统 Photo Picker 设计

日期：2026-08-04

状态：设计已批准，待书面规范复核

范围：新增可复用 Android Library 模块 **:album-api**，**:app** 仅作为宿主演示

## 1. 背景与目标

项目当前把 MediaStore 图片查询、相册目录、分页状态和 Compose 展示耦合在 **:app** 中。重构后，以 **:album-api** 为可复用边界，统一提供两类视觉媒体数据：

1. 宿主对当前媒体筛选范围拥有完整访问权限时，分页查询 MediaStore。
2. 宿主对当前媒体筛选范围只有部分访问权限或没有访问权限时，通过系统 Photo Picker 选择媒体，持久化 URI 读权限，并分页读取已选择记录。

媒体筛选统一支持：

- 只选择和查询图片。
- 只选择和查询视频。
- 同时选择和查询图片、视频。
- 默认只选择和查询图片，以保持现有行为。

最新确认的权限路由规则：

- FULL：走迁入 **:album-api** 的 MediaStore 分页查询。
- PARTIAL：走 Photo Picker 已选择记录的 Room 分页查询。
- DENIED：同样走 Photo Picker 已选择记录的 Room 分页查询。

设计目标：

- **:album-api** 不依赖 Compose、Material3、Coil 或任何自定义 UI。
- **:album-api** 提供统一媒体筛选、数据模型、权限状态判断、数据源路由、MediaStore 查询、Photo Picker 启动与结果持久化。
- 同一个媒体筛选条件贯穿权限判断、Photo Picker、MediaStore 分页、Room 分页和目录统计。
- **:app** 只声明并申请权限、调用库 API、收集分页数据并演示界面。
- Photo Picker 选择数量可配置；默认不施加库级数量上限。
- Photo Picker 返回的 URI 在进程重启后仍可访问。
- MediaStore 与 Photo Picker 对宿主暴露统一的数据模型。

## 2. 非目标

本次不包含：

- 在 **:album-api** 中提供 Compose、View、自定义相册页、图片预览或视频播放 UI。
- 在 **:album-api** 中申请媒体权限或展示权限说明弹窗。
- 音频、拍照、录像、裁剪、编辑、上传或云同步。
- 视频转码、缩略图文件生成或媒体内容缓存。
- 把 MediaStore 查询结果复制到 Room。
- 把 Android PARTIAL 权限当前允许访问的 MediaStore 子集导入 Room。
- 绕过 Android 系统对 Photo Picker 数量或授权范围的限制。
- 在库 Manifest 中替宿主静默声明读取媒体权限。

## 3. 模块边界

### 3.1 :album-api

新增 Android Library：

- Gradle 路径：**:album-api**
- namespace：**com.github.sceneren.album.api**
- 只提供 Android/Kotlin 数据与系统能力集成。

允许的运行时依赖：

- AndroidX Activity KTX：注册 Activity Result Contract 并启动系统 Photo Picker。
- AndroidX Lifecycle Runtime KTX：安全执行生命周期相关工作。
- AndroidX Paging Runtime：暴露 Flow<PagingData<AlbumMedia>>。
- AndroidX Room Runtime、KTX、Paging：持久化并分页读取 Photo Picker 选择记录。
- Kotlin Coroutines Android：线程切换和异步 API。

允许的构建期依赖：

- KSP：生成 Room 实现。

明确禁止的依赖：

- Compose 及 Paging Compose。
- Material3。
- Coil 或其他图片、视频加载 UI 库。
- Accompanist 权限组件。
- XXPermissions。
- 自定义 Activity、Fragment、Dialog 或 View。

Activity Result Contract 会启动系统提供的选择器界面，但库自身不实现 UI。

### 3.2 :app

**:app** 仅承担：

- 声明示例应用所需的图片和视频读取权限。
- 使用标准 Activity Result 权限 API 请求与当前媒体筛选相匹配的权限。
- 在 Activity 创建阶段注册库提供的 Photo Picker launcher。
- 调用 **AlbumApi** 获取统一分页数据。
- 在 Compose 中演示筛选切换、目录、媒体网格、加载状态和错误状态。
- 使用宿主 UI 图片加载能力展示图片和视频封面。
- 在权限回调与 onResume 时刷新数据源状态。

现有 **AlbumLoader**、MediaStore 查询模型和相关分页逻辑在能力迁入后从 **:app** 移除或替换，不保留两套实现。

## 4. 统一媒体筛选

库对外暴露：

    enum class AlbumMediaFilter {
        IMAGES,
        VIDEOS,
        IMAGES_AND_VIDEOS,
    }

规则：

- 所有接受 mediaFilter 的公开 API 默认使用 AlbumMediaFilter.IMAGES。
- IMAGES 只返回实际类型为 IMAGE 的项目。
- VIDEOS 只返回实际类型为 VIDEO 的项目。
- IMAGES_AND_VIDEOS 返回两种类型，并按同一稳定时间顺序混合分页。
- 筛选条件在一个 feed 或 launcher 的生命周期内不可变。
- 宿主切换筛选条件时创建新的 feed；已返回的 Flow 不在内部偷偷换筛选条件。

实际媒体类型与筛选条件分离：

    enum class AlbumMediaType {
        IMAGE,
        VIDEO,
    }

AlbumMediaFilter 是查询意图，AlbumMediaType 是单条结果的实际类型。混合筛选返回的每条 AlbumMedia 仍只有一个明确实际类型。

## 5. 权限与数据源路由

### 5.1 权限状态

库对外暴露：

    enum class MediaAccessStatus {
        FULL,
        PARTIAL,
        DENIED,
    }

权限状态相对于传入的 AlbumMediaFilter 计算，每次调用动态检查，不缓存。

Android 13 及以上的完整权限矩阵：

| mediaFilter | FULL 条件 |
|---|---|
| IMAGES | 已授予 READ_MEDIA_IMAGES |
| VIDEOS | 已授予 READ_MEDIA_VIDEO |
| IMAGES_AND_VIDEOS | READ_MEDIA_IMAGES 与 READ_MEDIA_VIDEO 均已授予 |

PARTIAL 条件：

- Android 14 及以上：当前筛选未满足 FULL，但已授予 READ_MEDIA_VISUAL_USER_SELECTED。
- IMAGES_AND_VIDEOS：只获得 READ_MEDIA_IMAGES 或只获得 READ_MEDIA_VIDEO 时也为 PARTIAL，包括 Android 13。

DENIED 条件：

- 当前筛选未满足 FULL。
- 未授予 READ_MEDIA_VISUAL_USER_SELECTED。
- 对混合筛选也未获得任一相关完整权限。

Android 12L 及以下：

- 授予 READ_EXTERNAL_STORAGE 时，三种筛选均为 FULL。
- 未授予时为 DENIED。
- 不存在 PARTIAL。

因此 PARTIAL 是库相对于媒体筛选定义的“不完整访问”状态，不只等同于 Android 14 的系统部分媒体权限。例如 Android 13 在混合筛选下只获得图片权限时也属于 PARTIAL。

Android 14 及以上的权限可能在应用进入后台后改变，宿主必须在恢复前台时重新获取状态和 feed。

### 5.2 自动路由

**AlbumApi.getMediaFeed(mediaFilter)** 创建 feed 时读取当前筛选对应的权限状态：

    FULL ───────── MediaStore PagingSource

    PARTIAL ───┐
               ├─ Photo Picker Room PagingSource
    DENIED ────┘

规则：

- 只有当前筛选为 FULL 时查询 MediaStore。
- PARTIAL 不查询 MediaStore，直接返回符合当前筛选的 Photo Picker 持久记录。
- DENIED 同样不查询 MediaStore，直接返回符合当前筛选的 Photo Picker 持久记录。
- Android 系统在 PARTIAL 状态下允许 MediaStore 看到的部分媒体不自动导入 Room，也不参与自动 feed。
- Photo Picker feed 只包含通过本库选择、成功持久化并符合当前筛选条件的记录。
- 权限或筛选变化不会替换已返回 Flow 的数据源。宿主重新调用 getMediaFeed 创建新 feed，避免混合分页状态。
- 从 MediaStore 切换到 Photo Picker，或改变媒体筛选时，宿主把目录筛选重置为“全部媒体”。

### 5.3 Manifest 责任

**:album-api** 的 Manifest 不声明以下权限：

- READ_EXTERNAL_STORAGE
- READ_MEDIA_IMAGES
- READ_MEDIA_VIDEO
- READ_MEDIA_VISUAL_USER_SELECTED
- WRITE_EXTERNAL_STORAGE

示例 **:app** 声明：

- READ_EXTERNAL_STORAGE，maxSdkVersion 为 32。
- READ_MEDIA_IMAGES。
- READ_MEDIA_VIDEO。
- READ_MEDIA_VISUAL_USER_SELECTED。

示例应用不声明 WRITE_EXTERNAL_STORAGE。

宿主按媒体筛选请求权限：

- Android 14 及以上：请求当前筛选所需的 READ_MEDIA_IMAGES、READ_MEDIA_VIDEO 或两者，并在同一次请求中包含 READ_MEDIA_VISUAL_USER_SELECTED。
- Android 13：请求当前筛选所需的 READ_MEDIA_IMAGES、READ_MEDIA_VIDEO 或两者。
- Android 12L 及以下：请求 READ_EXTERNAL_STORAGE。

库只判断权限，不主动请求权限。

若支持旧系统的 Photo Picker 回传模块，**:album-api** 可按 Android 官方方案合并 ModuleDependencies service 与 photopicker_activity metadata；这不是媒体读取权限，也不引入库自定义界面。

## 6. 对外 API

以单一入口对象承载数据库和数据源：

    class AlbumApi private constructor(context: Context) {
        companion object {
            fun create(context: Context): AlbumApi
        }

        fun getMediaAccessStatus(
            mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
        ): MediaAccessStatus

        fun getMediaFeed(
            mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
            bucketId: Long = AlbumDirectory.ALL_BUCKET_ID,
            pageSize: Int = DEFAULT_PAGE_SIZE,
        ): AlbumMediaFeed

        suspend fun getMediaDirectories(
            mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
        ): Result<List<AlbumDirectory>>

        fun registerPhotoPicker(
            activity: ComponentActivity,
            mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
            maxSelectionCount: Int? = null,
            onResult: (PhotoPickResult) -> Unit,
        ): AlbumPhotoPickerLauncher

        suspend fun removePersistedSelection(uri: Uri): Result<Boolean>

        suspend fun clearPersistedSelections(): Result<Int>

        suspend fun reconcilePersistedSelections(): Result<Int>
    }

约束：

- create 始终使用 applicationContext，避免持有 Activity。
- 同一进程中按数据库名复用 Room 实例。
- pageSize 必须大于 0，否则立即抛出 IllegalArgumentException。
- registerPhotoPicker 必须在 ComponentActivity 进入 STARTED 之前调用，推荐在 onCreate 中注册。
- 每个 launcher 固定 mediaFilter 和 maxSelectionCount；宿主需要其他组合时，在 onCreate 中注册另一个 launcher。
- 返回的 launcher 只持有 Activity Result launcher 和库入口所需引用，不把 Activity 存入单例。

### 6.1 统一 feed

    enum class AlbumMediaSource {
        MEDIA_STORE,
        PHOTO_PICKER,
    }

    data class AlbumMediaFeed(
        val mediaFilter: AlbumMediaFilter,
        val source: AlbumMediaSource,
        val accessStatus: MediaAccessStatus,
        val pagingData: Flow<PagingData<AlbumMedia>>,
    )

mediaFilter、source 和 accessStatus 是创建 feed 时的快照，使宿主能够明确展示筛选、目录、授权引导或 Photo Picker 入口。

### 6.2 统一媒体模型

    data class AlbumMedia(
        val uri: Uri,
        val mediaType: AlbumMediaType,
        val displayName: String?,
        val mimeType: String?,
        val sizeBytes: Long?,
        val dateAddedEpochSeconds: Long?,
        val dateModifiedEpochSeconds: Long?,
        val width: Int?,
        val height: Int?,
        val durationMillis: Long?,
        val bucketId: Long?,
        val bucketName: String?,
        val selectedAtEpochMillis: Long?,
        val source: AlbumMediaSource,
    )

字段规则：

- IMAGE 的 durationMillis 为 null。
- VIDEO 的 durationMillis 优先填写可查询值；无法可靠获取时为 null。
- MediaStore 项填写可查询到的日期、尺寸和目录字段，selectedAtEpochMillis 为 null。
- Photo Picker 项填写持久化时可读取到的 metadata，目录字段为 null，并填写 selectedAtEpochMillis。
- 无法可靠获得的可选 metadata 使用 null，不伪造 0 或空字符串。
- URI 始终是 content URI，宿主不依赖文件系统路径。
- mediaType 必须由 MediaStore 的 MEDIA_TYPE 或可信 MIME 顶级类型确定，不能为 null。

### 6.3 目录模型

    data class AlbumDirectory(
        val bucketId: Long,
        val bucketName: String?,
        val coverUri: Uri,
        val coverMediaType: AlbumMediaType,
        val mediaCount: Long,
    ) {
        companion object {
            const val ALL_BUCKET_ID: Long = Long.MIN_VALUE
        }
    }

规则：

- 只有当前筛选为 FULL 时查询目录。
- PARTIAL 与 DENIED 时 getMediaDirectories 返回 Result.success(emptyList())。
- 目录封面和 mediaCount 只统计符合当前 mediaFilter 的媒体。
- IMAGES_AND_VIDEOS 的封面可能是图片或视频，因此显式返回 coverMediaType。
- “全部媒体”由库用 ALL_BUCKET_ID 构造；bucketName 为 null，宿主负责本地化显示名称。
- Photo Picker 数据源不提供目录聚合。

## 7. Photo Picker 配置

### 7.1 媒体类型

AlbumMediaFilter 映射为 Activity Result Photo Picker 请求：

| AlbumMediaFilter | Photo Picker media type |
|---|---|
| IMAGES | PickVisualMedia.ImageOnly |
| VIDEOS | PickVisualMedia.VideoOnly |
| IMAGES_AND_VIDEOS | PickVisualMedia.ImageAndVideo |

单选和多选使用同一个媒体类型映射。

结果返回后再次通过 ContentResolver MIME 类型校验：

- IMAGES 只接受 image/ 开头的 MIME。
- VIDEOS 只接受 video/ 开头的 MIME。
- IMAGES_AND_VIDEOS 接受上述两类。
- MIME 类型为空、无法识别或不符合筛选条件时，整个批次返回 MEDIA_TYPE_NOT_ALLOWED。
- 不静默过滤不合规 URI，避免宿主误认为整个选择批次已保存。

### 7.2 选择数量

    maxSelectionCount == null
        默认模式：库不施加数量上限，使用系统多选能力允许的最大值。

    maxSelectionCount == 1
        使用 ActivityResultContracts.PickVisualMedia。

    maxSelectionCount > 1
        使用 ActivityResultContracts.PickMultipleVisualMedia(maxSelectionCount)。

    maxSelectionCount <= 0
        立即抛出 IllegalArgumentException。

“默认不限制”的准确含义是“不设置库级上限”。系统 Photo Picker 仍可能根据 Android 版本、设备实现和回退 contract 设置自己的最大值。

在 Photo Picker 不可用、Activity Result Contract 回退到 ACTION_OPEN_DOCUMENT 的系统上，平台可能忽略多选数量参数。因此库在结果返回后再次校验：

- 显式设置最大值且结果数量超限：整个批次失败，不持久化任何新记录或新授权。
- maxSelectionCount 为 null：不做库级数量截断。
- 不静默丢弃超出的 URI。

### 7.3 Launcher

    interface AlbumPhotoPickerLauncher {
        val mediaFilter: AlbumMediaFilter
        fun launch()
    }

每个 launcher 的 contract 类型、媒体筛选和数量上限在注册时确定。launch 不接受临时配置，防止注册参数与调用状态不一致。

### 7.4 选择结果

    sealed interface PhotoPickResult {
        data class Selected(
            val media: List<AlbumMedia>,
        ) : PhotoPickResult

        data object Cancelled : PhotoPickResult

        data class Failed(
            val reason: PhotoPickFailure,
            val cause: Throwable? = null,
        ) : PhotoPickResult
    }

    enum class PhotoPickFailure {
        SELECTION_LIMIT_EXCEEDED,
        MEDIA_TYPE_NOT_ALLOWED,
        PERSISTABLE_PERMISSION_FAILED,
        METADATA_READ_FAILED,
        DATABASE_WRITE_FAILED,
    }

语义：

- 单选和多选统一返回 List。
- 系统返回空列表时为 Cancelled。
- 同一次回调出现重复 URI 时按首次出现顺序去重。
- Selected 中每条媒体的实际 mediaType 都符合 launcher 的 mediaFilter。
- Selected 只在整个批次的数量校验、类型校验、授权、必要 metadata 读取和数据库写入全部成功后返回。
- 回调在主线程交付；校验、授权、metadata 与数据库操作在后台线程完成。

## 8. Photo Picker 持久访问与 Room

### 8.1 URI 授权

对每个选择结果调用 ContentResolver.takePersistableUriPermission，并仅请求读权限。

处理顺序：

1. 对 URI 按首次出现顺序去重。
2. 校验显式数量上限。
3. 解析 MIME 并校验媒体筛选。
4. 记录操作开始前已经存在的 persistedUriPermissions。
5. 对 URI 获取持久读授权。
6. 读取可用 metadata。
7. 在单个 Room 事务中写入整批记录。
8. 成功后返回 Selected。

失败补偿：

- 任一步失败时，Room 事务不留下部分批次。
- 仅释放本批次新获取的持久授权。
- 操作前已经存在的授权绝不因本批失败而释放。
- 已经存在于数据库中的记录保持原状。

ContentResolver 与 Room 不能共享真正的跨系统事务，因此这里的“原子”指 API 可观察结果：成功时整批可见，失败时不新增数据库记录，并尽力回收本批新授权。

### 8.2 表结构

Room 表 **picked_media**：

    @Entity(tableName = "picked_media")
    data class PickedMediaEntity(
        @PrimaryKey val uri: String,
        val mediaType: String,
        val displayName: String?,
        val mimeType: String?,
        val sizeBytes: Long?,
        val width: Int?,
        val height: Int?,
        val durationMillis: Long?,
        val selectedAtEpochMillis: Long,
        val sortOrder: Long,
        val ownsPersistableGrant: Boolean,
    )

约束：

- mediaType 只存储稳定值 IMAGE 或 VIDEO，映射为公开 AlbumMediaType。
- URI 是业务唯一键，重复选择执行更新而不是插入重复项。
- 重复选择会刷新 mediaType、metadata、selectedAtEpochMillis 和排序位置。
- 一个批次在串行事务中分配 sortOrder；第一个返回 URI 获得该批次最大的排序值，因此分页展示保持选择器返回顺序。
- DAO 使用 sortOrder DESC、uri ASC 作为稳定排序。
- ownsPersistableGrant 用于区分库本次或历史获取的授权与宿主原本已有的授权。

数据库只保存 Photo Picker 记录。MediaStore 数据不进入此表。

### 8.3 筛选分页

Photo Picker 数据使用按媒体类型过滤的 Room PagingSource：

    @Query(
        "SELECT * FROM picked_media " +
            "WHERE mediaType IN (:mediaTypes) " +
            "ORDER BY sortOrder DESC, uri ASC"
    )
    fun pagingSource(
        mediaTypes: List<String>,
    ): PagingSource<Int, PickedMediaEntity>

筛选映射：

- IMAGES 传入 IMAGE。
- VIDEOS 传入 VIDEO。
- IMAGES_AND_VIDEOS 传入 IMAGE、VIDEO。

Room invalidation 自动让新增、删除和清理结果刷新分页流。不同筛选的 Pager 分别持有自己的查询参数，不在已有 PagingSource 上修改条件。

### 8.4 删除、清理与对账

- removePersistedSelection：删除一条记录，并仅在 ownsPersistableGrant 为 true 时释放对应读授权。
- clearPersistedSelections：删除全部 Photo Picker 记录，并释放由库持有的对应授权。
- reconcilePersistedSelections：把数据库记录与 persistedUriPermissions 对账，删除已经失去持久授权或无法再打开的记录。
- 释放授权失败时 API 返回 Result.failure；数据库和授权的最终状态通过 reconcilePersistedSelections 可再次收敛。
- 对账覆盖图片和视频，在 Dispatchers.IO 运行，不阻塞主线程。

## 9. MediaStore 数据源

现有 **:app** 中的图片获取方式迁入并扩展为 **:album-api** 内部统一媒体实现：

- **MediaStoreAlbumDataSource**：目录查询和 ContentResolver 访问。
- **MediaStoreMediaPagingSource**：图片、视频或混合媒体分页。
- **MediaStoreSelectionFactory**：组合 mediaFilter 与 bucketId 条件。
- mapper：Cursor 到 AlbumMedia、AlbumDirectory。

### 9.1 查询范围与筛选

- 查询 MediaStore.Files 外部媒体集合。
- 用 MediaStore.Files.FileColumns.MEDIA_TYPE 筛选 MEDIA_TYPE_IMAGE、MEDIA_TYPE_VIDEO 或两者。
- 不查询音频、文档、播放列表或 MEDIA_TYPE_NONE。
- 只有当前 mediaFilter 对应的权限状态为 FULL 时调用此查询实现。
- PARTIAL 即使能通过系统权限看到一部分 MediaStore 项，也不会进入此数据源。
- 可选 bucketId 只在 MediaStore 数据源生效。
- bucketId 为 ALL_BUCKET_ID 时不添加 BUCKET_ID 条件。
- 混合筛选使用一次 Files 查询完成统一排序和分页，不分别查询图片、视频后在内存合并。

Cursor 映射：

- MEDIA_TYPE_IMAGE 映射为 AlbumMediaType.IMAGE。
- MEDIA_TYPE_VIDEO 映射为 AlbumMediaType.VIDEO。
- 根据实际媒体类型构造标准图片或视频 content URI。
- 查询通用尺寸、MIME、日期、bucket metadata，并为视频读取可用 duration。
- 不读取 MediaStore DATA，不返回原始文件路径。

### 9.2 分页兼容

- Android 11 及以上：使用 ContentResolver query Bundle 的 limit 和 offset 参数。
- Android 7 至 Android 10：沿用兼容的 SQL sortOrder LIMIT/OFFSET 方式。
- 默认排序为 DATE_ADDED DESC、_ID DESC，图片和视频共享同一稳定顺序。
- mediaFilter 与 bucketId 条件同时参与每一页查询。
- nextKey 只在返回数量达到请求数量时生成。
- refreshKey 以 anchorPosition 对应页为基础计算。
- Cursor 始终通过 use 关闭。
- 查询和 Cursor 映射在 Dispatchers.IO 执行。

分页异常通过 PagingSource.LoadResult.Error 交给宿主处理，不吞掉 SecurityException。若权限在查询期间改变，宿主下一次 onResume 重新获取 feed；当前 PagingSource 的错误仍可被 UI 明确展示。

### 9.3 目录查询

- 统计每个可访问 bucket 中符合 mediaFilter 的媒体数量。
- 每个 bucket 的第一条稳定排序媒体作为封面，并记录 coverMediaType。
- 返回“全部媒体”虚拟目录加各 bucket。
- 目录顺序保持稳定；虚拟目录始终第一项，其余按最近媒体时间降序、bucketId 作为次排序。
- IMAGES 目录只统计图片，VIDEOS 目录只统计视频，混合目录统计两者。
- 所有数量、封面和目录都只反映当前筛选与完整权限允许读取的范围。

## 10. :app 宿主演示

### 10.1 权限交互

宿主使用标准 Activity Result 权限 API，并根据当前 AlbumMediaFilter 构造权限数组：

- IMAGES 请求图片权限。
- VIDEOS 请求视频权限。
- IMAGES_AND_VIDEOS 请求图片和视频权限。
- Android 14 及以上把 READ_MEDIA_VISUAL_USER_SELECTED 与相关读取权限放在同一次请求中。
- Android 12L 及以下统一请求 READ_EXTERNAL_STORAGE。

切换媒体筛选后，如果新的筛选缺少完整权限，宿主可在明确用户操作后请求缺失权限；库本身不弹权限框。

### 10.2 ViewModel 状态

示例 ViewModel 维护：

- 当前 AlbumMediaFilter。
- 当前 AlbumMediaFeed。
- 当前 MediaAccessStatus 和 AlbumMediaSource。
- MediaStore 模式下的目录列表与 bucketId。
- Photo Picker 最近一次选择结果或错误。

刷新时机：

- 首次进入页面。
- 媒体筛选变化。
- 权限请求回调。
- Activity onResume。
- 用户主动重试。

每次刷新重新调用 getMediaFeed。若 mediaFilter 或 source 改变，取消收集旧 flow，重置目录选择，再收集新 flow。

### 10.3 Compose 展示

Compose 只存在于 **:app**：

- 提供图片、视频、图片和视频三种演示筛选。
- 使用 Paging Compose 收集 AlbumMedia。
- 使用 Coil 或宿主配置的视频帧解码能力加载 AlbumMedia.uri。
- VIDEO 项展示视频标识，可在 durationMillis 非空时展示时长。
- FULL 展示符合筛选的 MediaStore 媒体与目录。
- PARTIAL/DENIED 展示符合筛选的 Photo Picker 持久记录，并提供相应的“选择媒体”入口。
- 用户即使处于 FULL，宿主也可保留 Photo Picker 入口；选择结果会保存，但自动 feed 在 FULL 状态仍展示 MediaStore。
- 示例 Activity 在 onCreate 中注册图片、视频和混合类型所需 launcher，Compose 只调用已经注册的 launcher。
- 不把 Activity、launcher 或 ContentResolver 放入 composable 数据模型。

## 11. 并发、生命周期与错误

- AlbumApi 使用 applicationContext。
- Room 批量写入和 grant 处理串行化，防止两个选择结果竞争 sortOrder。
- launcher 必须在 Activity STARTED 前注册；销毁 Activity 后不再调用旧 launcher。
- onResult 只有一次终态回调。
- 如果 Activity 在后台任务完成前销毁，数据库操作仍可完成；回调只在其 lifecycle 仍可安全交付时调用。
- 所有公开 suspend API 支持协程取消。
- 类型或数量校验失败发生在获取新持久授权和写数据库之前。
- 可选 metadata 缺失不会导致批次失败；无法确定实际图片或视频类型才会失败。
- SecurityException、SQLiteException 和 metadata 解析错误保留 cause，便于宿主日志和诊断。
- 不在日志中输出媒体内容、文件路径或大批 URI 列表。

## 12. 迁移步骤

1. settings.gradle.kts 注册 **:album-api**。
2. 创建 Android Library Gradle 配置与 Manifest。
3. 在版本目录中补充 Paging、Room、KSP、Coroutines 和所需 AndroidX Activity/Lifecycle 依赖，不覆盖用户现有版本升级。
4. 在 **:album-api** 建立 AlbumMediaFilter、AlbumMediaType、统一模型、权限判断与入口 API。
5. 把当前 **:app** 的 MediaStore 图片查询和目录逻辑迁入库，扩展为 Files 图片/视频统一 PagingSource。
6. 实现 Photo Picker 图片、视频、混合筛选及单选/多选 launcher。
7. 实现持久 URI 授权、媒体类型校验和 Room 批量存储。
8. 实现 FULL 到 MediaStore、PARTIAL/DENIED 到 Photo Picker Room 的自动路由。
9. 重构 **:app** 为筛选、权限申请和 Compose 演示宿主。
10. 删除已经被库替代的 app 内 AlbumLoader、旧图片模型和重复数据层。
11. 更新项目参考扫描与模块文档。

## 13. 测试设计

### 13.1 album-api 单元测试

媒体筛选：

- 三种筛选正确映射到图片、视频或两种实际类型。
- 所有公开 API 默认使用 IMAGES。
- feed 快照包含请求时的 mediaFilter。

权限状态：

- API 34+ 分别验证 IMAGES、VIDEOS、IMAGES_AND_VIDEOS 的 FULL。
- API 34+ 仅 READ_MEDIA_VISUAL_USER_SELECTED 时为 PARTIAL。
- 混合筛选只获得图片或只获得视频完整权限时为 PARTIAL。
- 单一筛选只获得无关媒体权限时为 DENIED。
- API 33 混合筛选只获得一种权限时为 PARTIAL。
- API 32 有无 READ_EXTERNAL_STORAGE 的版本分支正确。

路由：

- 三种筛选在 FULL 时都创建对应 MediaStore feed。
- 三种筛选在 PARTIAL 时都创建对应 Room Photo Picker feed。
- 三种筛选在 DENIED 时都创建对应 Room Photo Picker feed。
- PARTIAL/DENIED 的目录查询返回空列表。

MediaStore：

- IMAGES 只生成 MEDIA_TYPE_IMAGE 条件。
- VIDEOS 只生成 MEDIA_TYPE_VIDEO 条件。
- IMAGES_AND_VIDEOS 一次查询两种类型。
- 图片和视频混合首页、下一页、尾页和 refreshKey。
- DATE_ADDED 相同的项目通过 _ID 稳定排序。
- Android 11+ query Bundle 分页参数。
- Android 7 至 10 的兼容 LIMIT/OFFSET。
- mediaFilter 与 bucket 筛选组合正确。
- 三种筛选下“全部媒体”、目录数量、封面和 coverMediaType 正确。
- IMAGE 的 durationMillis 为 null，VIDEO 映射可用 duration。
- SecurityException 转为 LoadResult.Error。

Photo Picker 类型与数量：

- IMAGES 映射 ImageOnly。
- VIDEOS 映射 VideoOnly。
- IMAGES_AND_VIDEOS 映射 ImageAndVideo。
- null 注册默认多选。
- 1 注册 PickVisualMedia。
- 大于 1 注册 PickMultipleVisualMedia。
- 0 和负数拒绝。
- 回退 contract 超出显式上限时整批失败。
- MIME 不符合筛选或无法识别时整批失败且不获取新授权。

Photo Picker 持久化：

- 空结果为 Cancelled。
- URI 去重并保持首次出现顺序。
- 图片、视频和混合批次正确写入 mediaType。
- 视频 duration metadata 可用时正确保存，不可用时为 null。
- 批量授权、metadata 和 Room 写入成功后返回 Selected。
- 任一授权失败不写入部分数据库。
- 必要 metadata 或数据库失败回滚本批新授权。
- 既有授权不被失败补偿释放。
- 重复选择更新类型与排序，不生成重复记录。

Room：

- IMAGES 只分页图片记录。
- VIDEOS 只分页视频记录。
- IMAGES_AND_VIDEOS 分页两种记录并保持稳定顺序。
- remove、clear 和 reconcile 覆盖图片和视频授权。
- 数据库变更触发分页失效。

### 13.2 app 测试

- 切换三种媒体筛选后重新获取权限状态、目录和 feed。
- 权限回调后重新获取 feed。
- onResume 发现 FULL、PARTIAL、DENIED 变化时正确切换数据源。
- 筛选或 source 变化时目录重置。
- FULL 展示符合筛选的 MediaStore 分页。
- PARTIAL/DENIED 展示符合筛选的持久列表和选择入口。
- 图片、视频封面及视频时长状态正确。
- 空态、加载、重试和错误状态。

### 13.3 真机验证

- Android 14 及以上分别验证图片、视频和混合筛选的完整、部分和拒绝权限。
- Android 13 分别验证 READ_MEDIA_IMAGES、READ_MEDIA_VIDEO 及组合权限。
- Android 12L 或以下验证旧读取权限和 Photo Picker 回退。
- 三种媒体筛选分别验证单选、显式多选上限和默认多选。
- 混合选择结果同时包含图片和视频。
- 选择后杀进程并重启，图片和视频 URI 仍可读取。
- 在 FULL、PARTIAL、DENIED 之间切换后返回应用，feed 按规则切换且不混用旧分页流。
- 切换 mediaFilter 后旧 PagingSource 不再被 UI 收集。
- 清理选择后记录和库持有的授权同步移除。

## 14. 构建与验收

至少执行：

    .\gradlew.bat :album-api:testDebugUnitTest --console=plain
    .\gradlew.bat :app:testDebugUnitTest --console=plain
    .\gradlew.bat :app:assembleDebug --console=plain

有设备或模拟器时执行：

    .\gradlew.bat :app:connectedDebugAndroidTest --console=plain

并重新生成项目参考：

    python .codex/scripts/gen_references.py
    python .codex/scripts/gen_references.py --diff

验收标准：

- **:album-api** 的依赖图中没有 Compose 或 UI 库。
- **:app** 不再包含 MediaStore 数据层的重复实现。
- IMAGES、VIDEOS、IMAGES_AND_VIDEOS 统一作用于权限、MediaStore、Photo Picker、Room 和目录。
- 三种筛选默认行为明确，未传 mediaFilter 时保持只选图片。
- FULL 使用库内 MediaStore.Files 分页。
- PARTIAL 和 DENIED 使用符合筛选的 Photo Picker 持久记录分页。
- Photo Picker 默认无库级数量上限，显式上限能够严格校验。
- 所有成功返回的 Photo Picker 图片和视频在重启后可读。
- 权限或筛选变化后宿主能刷新到正确数据源。
- 单元测试和 debug 构建通过。

## 15. 已确认决策

- 复用边界是 **:album-api**；**:app** 仅作宿主演示。
- API 模块不引入 Compose 或自定义 UI，只提供数据和系统 Photo Picker 集成。
- Photo Picker 由库注册和管理 Activity Result launcher。
- 选择数量可配置，默认不施加库级限制。
- 媒体类型可配置为只选图片、只选视频、图片和视频。
- 同一个 AlbumMediaFilter 统一作用于 Photo Picker、MediaStore 分页、Room 分页、目录和权限判断。
- 未指定媒体筛选时默认 IMAGES。
- 当前 app 的 MediaStore 图片获取方式迁入 **:album-api**，并扩展为图片和视频统一查询。
- 只有当前筛选为 FULL 时使用 MediaStore 分页查询。
- PARTIAL 与 DENIED 均使用符合当前筛选的 Photo Picker 持久选择记录。
- URI 访问权限由库获取、持久化、对账和按所有权释放。

## 16. 本次批准的设计修订

相对上一版的新增变化：

1. 新增 AlbumMediaFilter，支持 IMAGES、VIDEOS、IMAGES_AND_VIDEOS。
2. 媒体筛选统一作用于 Photo Picker 和 MediaStore 分页，同时覆盖 Room、目录与权限判断。
3. 默认筛选为 IMAGES，保持现有图片相册行为。
4. MediaStore 从图片集合扩展为 Files 统一图片/视频查询。
5. 统一模型增加 AlbumMediaType 和 durationMillis，目录增加 coverMediaType 并把 imageCount 改为 mediaCount。
6. 权限判断改为相对于筛选范围计算；混合筛选缺少任一完整权限即为 PARTIAL 或 DENIED。
7. Photo Picker 与回退结果增加 MIME 类型二次校验，批次不符合筛选时原子失败。
8. Room 按媒体类型持久化并支持三种筛选分页。

书面规范复核通过后进入详细实施计划和测试驱动实现，本设计阶段不修改生产代码。

## 17. 官方技术依据

- Android Photo Picker：https://developer.android.com/training/data-storage/shared/photo-picker
- MediaStore.Files.FileColumns：https://developer.android.com/reference/android/provider/MediaStore.Files.FileColumns
- Android 14 部分图片和视频访问：https://developer.android.com/about/versions/14/changes/partial-photo-video-access
