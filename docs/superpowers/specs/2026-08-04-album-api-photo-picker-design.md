# album-api 相册数据与系统照片选择器设计

日期：2026-08-04

状态：待再次批准
范围：新增可复用 Android Library 模块 **:album-api**，**:app** 仅作为宿主演示

## 1. 背景与目标

项目当前把 MediaStore 查询、相册目录、分页状态和 Compose 展示耦合在 **:app** 中。重构后，以 **:album-api** 为可复用边界，统一提供两类图片数据：

1. 宿主拥有完整媒体访问权限时，分页查询 MediaStore。
2. 宿主只有部分媒体访问权限或没有媒体访问权限时，通过系统 Photo Picker 选择图片，持久化 URI 访问权限，并分页读取已选择记录。

最新确认的权限路由规则是：

- FULL：走迁入 **:album-api** 的 MediaStore 分页查询。
- PARTIAL：走 Photo Picker 已选择记录的 Room 分页查询。
- DENIED：同样走 Photo Picker 已选择记录的 Room 分页查询。

设计目标：

- **:album-api** 不依赖 Compose、Material3、Coil 或任何自定义 UI。
- **:album-api** 提供数据模型、权限状态判断、数据源路由、MediaStore 查询、Photo Picker 启动与结果持久化。
- **:app** 只声明并申请权限、调用库 API、收集分页数据并演示界面。
- Photo Picker 选择张数可配置；默认不施加库级数量上限。
- Photo Picker 返回的 URI 在进程重启后仍可访问。
- MediaStore 与 Photo Picker 对宿主暴露统一的数据模型。

## 2. 非目标

本次不包含：

- 在 **:album-api** 中提供 Compose、View、自定义相册页或图片预览 UI。
- 在 **:album-api** 中申请媒体权限或展示权限说明弹窗。
- 视频、音频、拍照、裁剪、编辑、上传或云同步。
- 把 MediaStore 查询结果复制到 Room。
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
- Coil 或其他图片加载 UI 库。
- Accompanist 权限组件。
- XXPermissions。
- 自定义 Activity、Fragment、Dialog 或 View。

Activity Result Contract 会启动系统提供的选择器界面，但库自身不实现 UI。

### 3.2 :app

**:app** 仅承担：

- 声明示例应用所需的读取图片权限。
- 使用标准 Activity Result 权限 API 请求权限。
- 在 Activity 创建阶段注册库提供的 Photo Picker launcher。
- 调用 **AlbumApi** 获取统一分页数据。
- 在 Compose 中演示目录、图片网格、加载状态和错误状态。
- 在权限回调与 onResume 时刷新数据源状态。

现有 **AlbumLoader**、MediaStore 查询模型和相关分页逻辑在能力迁入后从 **:app** 移除或替换，不保留两套实现。

## 4. 权限与数据源路由

### 4.1 权限状态

库对外暴露：

    enum class MediaAccessStatus {
        FULL,
        PARTIAL,
        DENIED,
    }

每次调用时动态检查，不缓存权限状态：

- Android 13 及以上：授予 READ_MEDIA_IMAGES 时为 FULL。
- Android 14 及以上：未授予 READ_MEDIA_IMAGES，但授予 READ_MEDIA_VISUAL_USER_SELECTED 时为 PARTIAL。
- Android 12L 及以下：授予 READ_EXTERNAL_STORAGE 时为 FULL。
- 其他情况为 DENIED。

Android 13 不存在 PARTIAL 分支。Android 14 及以上的权限可能在应用处于后台时改变，因此宿主必须在恢复前台时重新获取 feed。

### 4.2 自动路由

**AlbumApi.getMediaFeed** 在创建 feed 时读取当前权限状态：

    FULL ───────── MediaStore PagingSource

    PARTIAL ───┐
               ├─ Photo Picker Room PagingSource
    DENIED ────┘

规则：

- 只有 FULL 查询 MediaStore。
- PARTIAL 不查询 MediaStore，直接返回已持久化 Photo Picker 记录。
- DENIED 同样不查询 MediaStore，直接返回已持久化 Photo Picker 记录。
- Android 系统在 PARTIAL 状态下允许 MediaStore 看到的部分图片不自动导入 Room，也不参与自动 feed；Photo Picker feed 只包含通过本库选择并成功持久化的记录。
- 权限变化不会偷偷替换一个已返回 Flow 的数据源。宿主通过重新调用 getMediaFeed 明确创建新 feed，避免混合两个分页流的状态。
- 当数据源从 MediaStore 切换到 Photo Picker 时，宿主把目录筛选重置为“全部图片”。

### 4.3 Manifest 责任

**:album-api** 的 Manifest 不声明以下权限：

- READ_EXTERNAL_STORAGE
- READ_MEDIA_IMAGES
- READ_MEDIA_VISUAL_USER_SELECTED
- WRITE_EXTERNAL_STORAGE

示例 **:app** 声明：

- READ_EXTERNAL_STORAGE，maxSdkVersion 为 32。
- READ_MEDIA_IMAGES。
- READ_MEDIA_VISUAL_USER_SELECTED。

不声明视频权限和写存储权限。

若支持旧系统的 Photo Picker 回传模块，**:album-api** 可按 Android 官方方案合并 ModuleDependencies service 与 photopicker_activity metadata；这不是媒体读取权限，也不引入库自定义界面。

## 5. 对外 API

建议以单一入口对象承载数据库和数据源：

    class AlbumApi private constructor(context: Context) {
        companion object {
            fun create(context: Context): AlbumApi
        }

        fun getMediaAccessStatus(): MediaAccessStatus

        fun getMediaFeed(
            bucketId: Long = AlbumDirectory.ALL_BUCKET_ID,
            pageSize: Int = DEFAULT_PAGE_SIZE,
        ): AlbumMediaFeed

        suspend fun getImageDirectories(): Result<List<AlbumDirectory>>

        fun registerPhotoPicker(
            activity: ComponentActivity,
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
- 返回的 launcher 只持有 Activity Result launcher 和库入口所需引用，不把 Activity 存入单例。

### 5.1 统一 feed

    enum class AlbumMediaSource {
        MEDIA_STORE,
        PHOTO_PICKER,
    }

    data class AlbumMediaFeed(
        val source: AlbumMediaSource,
        val accessStatus: MediaAccessStatus,
        val pagingData: Flow<PagingData<AlbumMedia>>,
    )

**source** 和 **accessStatus** 是创建 feed 时的快照，使宿主能够决定是否展示目录筛选、授权引导或 Photo Picker 入口。

### 5.2 统一媒体模型

    data class AlbumMedia(
        val uri: Uri,
        val displayName: String?,
        val mimeType: String?,
        val sizeBytes: Long?,
        val dateAddedEpochSeconds: Long?,
        val dateModifiedEpochSeconds: Long?,
        val width: Int?,
        val height: Int?,
        val bucketId: Long?,
        val bucketName: String?,
        val selectedAtEpochMillis: Long?,
        val source: AlbumMediaSource,
    )

字段规则：

- MediaStore 项填写可查询到的日期、尺寸和目录字段，selectedAtEpochMillis 为 null。
- Photo Picker 项填写持久化时可读取到的 metadata，目录字段为 null，并填写 selectedAtEpochMillis。
- 无法可靠获得的 metadata 使用 null，不伪造 0 或空字符串。
- URI 始终是 content URI，宿主不依赖文件系统路径。

### 5.3 目录模型

    data class AlbumDirectory(
        val bucketId: Long,
        val bucketName: String?,
        val coverUri: Uri,
        val imageCount: Long,
    ) {
        companion object {
            const val ALL_BUCKET_ID: Long = Long.MIN_VALUE
        }
    }

规则：

- FULL 时目录统计覆盖所有可读图片。
- PARTIAL 与 DENIED 时 getImageDirectories 返回 Result.success(emptyList())。
- “全部图片”由库构造，图片数量也是当前可访问范围内的数量。
- Photo Picker 数据源不提供目录聚合。

## 6. Photo Picker 选择数量

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
- 不静默丢弃超出的 URI，避免宿主误认为所有选择都已保存。

### 6.1 Launcher

    interface AlbumPhotoPickerLauncher {
        fun launch()
    }

每个 launcher 的 contract 类型在注册时确定。launch 不接受临时数量参数，防止注册 contract 与调用参数不一致；宿主需要不同上限时注册不同 launcher。

### 6.2 选择结果

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
        PERSISTABLE_PERMISSION_FAILED,
        METADATA_READ_FAILED,
        DATABASE_WRITE_FAILED,
    }

语义：

- 单选和多选统一返回 List。
- 系统返回空列表时为 Cancelled。
- 同一次回调出现重复 URI 时按首次出现顺序去重。
- Selected 只在整个批次的授权、metadata 读取和数据库写入全部成功后返回。
- 回调在主线程交付；授权、metadata 与数据库操作在后台线程完成。

## 7. Photo Picker 持久访问与 Room

### 7.1 URI 授权

对每个选择结果调用 ContentResolver.takePersistableUriPermission，并仅请求读权限。

处理顺序：

1. 记录操作开始前已经存在的 persistedUriPermissions。
2. 对去重后的 URI 获取持久读授权。
3. 读取可用 metadata。
4. 在单个 Room 事务中写入整批记录。
5. 成功后返回 Selected。

失败补偿：

- 任一步失败时，Room 事务不留下部分批次。
- 仅释放本批次新获取的持久授权。
- 操作前已经存在的授权绝不因本批失败而释放。
- 已经存在于数据库中的记录保持原状。

ContentResolver 与 Room 不能共享真正的跨系统事务，因此这里的“原子”指 API 可观察结果：成功时整批可见，失败时不新增数据库记录，并尽力回收本批新授权。

### 7.2 表结构

Room 表 **picked_media**：

    @Entity(tableName = "picked_media")
    data class PickedMediaEntity(
        @PrimaryKey val uri: String,
        val displayName: String?,
        val mimeType: String?,
        val sizeBytes: Long?,
        val width: Int?,
        val height: Int?,
        val selectedAtEpochMillis: Long,
        val sortOrder: Long,
        val ownsPersistableGrant: Boolean,
    )

约束：

- URI 是业务唯一键，重复选择执行更新而不是插入重复项。
- 重复选择会刷新 metadata、selectedAtEpochMillis 和排序位置。
- 一个批次在串行事务中分配 sortOrder；第一个返回 URI 获得该批次最大的排序值，因此分页展示保持选择器返回顺序。
- DAO 使用 sortOrder DESC、uri ASC 作为稳定排序。
- ownsPersistableGrant 用于区分库本次/历史获取的授权与宿主原本已有的授权。

数据库只保存 Photo Picker 记录。MediaStore 数据不进入此表。

### 7.3 分页

Photo Picker 数据使用 Room PagingSource：

    @Query(
        "SELECT * FROM picked_media " +
            "ORDER BY sortOrder DESC, uri ASC"
    )
    fun pagingSource(): PagingSource<Int, PickedMediaEntity>

Room 的 invalidation 自动让新增、删除和清理结果刷新分页流。

### 7.4 删除、清理与对账

- removePersistedSelection：删除一条记录，并仅在 ownsPersistableGrant 为 true 时释放对应读授权。
- clearPersistedSelections：删除全部 Photo Picker 记录，并释放由库持有的对应授权。
- reconcilePersistedSelections：把数据库记录与 persistedUriPermissions 对账，删除已经失去持久授权或无法再打开的记录。
- 释放授权失败时 API 返回 Result.failure；数据库和授权的最终状态通过 reconcilePersistedSelections 可再次收敛。
- 对账在 Dispatchers.IO 运行，不阻塞主线程。

## 8. MediaStore 数据源

现有 **:app** 中的图片获取方式迁入并重构为 **:album-api** 内部实现：

- **MediaStoreAlbumDataSource**：目录查询和 ContentResolver 访问。
- **MediaStoreImagePagingSource**：图片分页。
- mapper：Cursor 到 AlbumMedia、AlbumDirectory。

### 8.1 查询范围

- 只查询 MediaStore.Images.Media.EXTERNAL_CONTENT_URI。
- 只有 FULL 调用此查询实现。
- PARTIAL 即使能够通过系统权限看到一部分 MediaStore 图片，也不会进入此数据源。
- 可选 bucketId 只在 MediaStore 数据源生效。
- bucketId 为 ALL_BUCKET_ID 时不添加 BUCKET_ID 条件。

### 8.2 分页兼容

- Android 11 及以上：使用 ContentResolver query Bundle 的 limit 和 offset 参数。
- Android 7 至 Android 10：沿用兼容的 SQL sortOrder LIMIT/OFFSET 方式。
- 默认排序为 DATE_ADDED DESC、_ID DESC，避免相同时间戳导致跨页抖动。
- nextKey 只在返回数量达到请求数量时生成。
- refreshKey 以 anchorPosition 对应页为基础计算。
- Cursor 始终通过 use 关闭。
- 查询和 Cursor 映射在 Dispatchers.IO 执行。

分页异常通过 PagingSource.LoadResult.Error 交给宿主处理，不吞掉 SecurityException。若权限在查询期间改变，宿主下一次 onResume 重新获取 feed；当前 PagingSource 的错误仍可被 UI 明确展示。

### 8.3 目录查询

- 统计每个可访问 bucket 的图片数量。
- 每个 bucket 的第一张稳定排序图片作为封面。
- 返回“全部图片”加各 bucket。
- 目录顺序保持稳定；“全部图片”始终第一项，其余按最近图片时间降序、bucketId 作为次排序。
- 所有数量、封面和目录都只反映当前系统允许读取的范围。

## 9. :app 宿主演示

### 9.1 权限交互

宿主使用标准 Activity Result 权限 API：

- Android 14 及以上同时请求 READ_MEDIA_IMAGES 和 READ_MEDIA_VISUAL_USER_SELECTED。
- Android 13 请求 READ_MEDIA_IMAGES。
- Android 12L 及以下请求 READ_EXTERNAL_STORAGE。

库只负责判断结果状态，不主动申请权限。

### 9.2 ViewModel 状态

示例 ViewModel 维护：

- 当前 AlbumMediaFeed。
- 当前 MediaAccessStatus 和 AlbumMediaSource。
- MediaStore 模式下的目录列表与 bucketId。
- Photo Picker 最近一次选择结果或错误。

刷新时机：

- 首次进入页面。
- 权限请求回调。
- Activity onResume。
- 用户主动重试。

每次刷新重新调用 getMediaFeed。若 source 改变，取消收集旧 flow，重置目录选择，再收集新 flow。

### 9.3 Compose 展示

Compose 只存在于 **:app**：

- 使用 Paging Compose 收集 AlbumMedia。
- 使用 Coil 加载 AlbumMedia.uri。
- FULL 展示 MediaStore 图片与目录。
- PARTIAL/DENIED 展示已持久化的 Photo Picker 选择记录，并提供“选择图片”按钮。
- 用户即使处于 FULL，宿主也可按产品需要保留 Photo Picker 入口；选择结果会保存，但自动 feed 在 FULL 状态仍展示 MediaStore。
- 不把 Activity、launcher 或 ContentResolver 放入 composable 数据模型。

## 10. 并发、生命周期与错误

- AlbumApi 使用 applicationContext。
- Room 批量写入和 grant 处理串行化，防止两个选择结果竞争 sortOrder。
- launcher 必须在 Activity STARTED 前注册；销毁 Activity 后不再调用旧 launcher。
- onResult 只有一次终态回调。
- 如果 Activity 在后台任务完成前销毁，数据库操作仍可完成；回调只在其 lifecycle 仍可安全交付时调用。
- 所有公开 suspend API 支持协程取消。
- SecurityException、SQLiteException 和 metadata 解析错误保留 cause，便于宿主日志和诊断。
- 不在日志中输出图片内容、文件路径或大批 URI 列表。

## 11. 迁移步骤

1. settings.gradle.kts 注册 **:album-api**。
2. 创建 Android Library Gradle 配置与 Manifest。
3. 在版本目录中补充 Paging、Room、KSP、Coroutines 和所需 AndroidX Activity/Lifecycle 依赖，不覆盖用户现有版本升级。
4. 在 **:album-api** 建立统一模型、权限判断与入口 API。
5. 把当前 **:app** 的 MediaStore 查询和目录逻辑迁入库，并改造成 PagingSource。
6. 实现 Photo Picker 单选/多选 launcher、持久 URI 授权和 Room 批量存储。
7. 实现 FULL 到 MediaStore、PARTIAL/DENIED 到 Photo Picker Room 的自动路由。
8. 重构 **:app** 为权限申请和 Compose 演示宿主。
9. 删除已经被库替代的 app 内 AlbumLoader、旧媒体模型和重复数据层。
10. 更新项目参考扫描与模块文档。

## 12. 测试设计

### 12.1 album-api 单元测试

权限状态：

- API 34+ 完整权限映射为 FULL。
- API 34+ 仅用户选择权限映射为 PARTIAL。
- API 34+ 两者都无映射为 DENIED。
- API 33 与 API 32 的版本分支正确。

路由：

- FULL 创建 MediaStore feed。
- PARTIAL 创建 Room Photo Picker feed。
- DENIED 创建 Room Photo Picker feed。
- PARTIAL/DENIED 的目录查询返回空列表。

MediaStore：

- 首页、下一页、尾页和 refreshKey。
- DATE_ADDED 相同的项目通过 _ID 稳定排序。
- Android 11+ query Bundle 分页参数。
- Android 7 至 10 的兼容 LIMIT/OFFSET。
- bucket 筛选、“全部图片”、目录数量与封面。
- SecurityException 转为 LoadResult.Error。

Photo Picker 数量：

- null 注册默认多选。
- 1 注册 PickVisualMedia。
- 大于 1 注册 PickMultipleVisualMedia。
- 0 和负数拒绝。
- 回退 contract 超出显式上限时整批失败。

Photo Picker 持久化：

- 空结果为 Cancelled。
- URI 去重并保持首次出现顺序。
- 批量授权、metadata 和 Room 写入成功后返回 Selected。
- 任一授权失败不写入部分数据库。
- metadata 或数据库失败回滚本批新授权。
- 既有授权不被失败补偿释放。
- 重复选择更新排序，不生成重复记录。

Room：

- PagingSource 使用稳定顺序。
- remove、clear 和 reconcile 的记录与授权行为。
- 数据库变更触发分页失效。

### 12.2 app 测试

- 权限回调后重新获取 feed。
- onResume 发现 FULL、PARTIAL、DENIED 变化时正确切换数据源。
- 切换到 Photo Picker 时目录重置。
- FULL 展示 MediaStore 分页。
- PARTIAL/DENIED 展示持久化选择列表和选择按钮。
- 空态、加载、重试和错误状态。

### 12.3 真机验证

- Android 14 及以上分别验证完整、部分和拒绝权限。
- Android 13 验证完整与拒绝权限。
- Android 12L 或以下验证旧读取权限和 Photo Picker 回退。
- 单选、显式多选上限、默认多选。
- 选择后杀进程并重启，URI 仍可读取。
- 在 FULL、PARTIAL、DENIED 之间切换后返回应用，feed 按规则切换且不混用旧分页流。
- 清理选择后记录和库持有的授权同步移除。

## 13. 构建与验收

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
- FULL 使用库内 MediaStore 分页。
- PARTIAL 和 DENIED 使用 Photo Picker 持久记录分页。
- Photo Picker 默认无库级上限，显式上限能够严格校验。
- 所有成功返回的 Photo Picker 项在重启后可读。
- 权限变化后宿主能通过刷新切换到正确数据源。
- 单元测试和 debug 构建通过。

## 14. 已确认决策

- 复用边界是 **:album-api**；**:app** 仅作宿主演示。
- API 模块不引入 Compose 或自定义 UI，只提供数据和系统 Photo Picker 集成。
- Photo Picker 由库注册和管理 Activity Result launcher。
- 选择数量可配置，默认不施加库级限制。
- 当前 app 的 MediaStore 获取方式迁入 **:album-api**，不再删除这项能力。
- 只有 FULL 使用迁入后的 MediaStore 分页查询。
- PARTIAL 与 DENIED 均使用 Photo Picker 持久选择记录。
- URI 访问权限由库获取、持久化、对账和按所有权释放。

## 15. 待批准点

本次修订相对上一版的关键变化：

1. 恢复并迁移现有 MediaStore 图片获取能力。
2. 增加 FULL、PARTIAL、DENIED 自动路由。
3. 明确只有 FULL 走 MediaStore，PARTIAL 与 DENIED 都走 Photo Picker 持久选择分页。
4. Photo Picker 从固定单选扩展为可配置单选/多选，默认无库级上限。
5. 选择结果与 Room 写入改为批次原子语义。
6. **:app** 增加宿主权限声明、请求与权限变化刷新职责。

批准后进入详细实施计划和测试驱动实现，不在本设计阶段修改生产代码。
