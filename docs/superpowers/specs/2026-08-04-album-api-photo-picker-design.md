# Album API Photo Picker 设计

## 背景

当前项目是单模块 Android 应用，`:app` 同时承载 MediaStore 查询、权限申请、分页、Photo Picker 启动和 Compose 演示。目标是整体重构：新增可复用的 `:album-api` Android Library，由它提供图片选择、持久 URI 授权、本地存储和分页数据；`:app` 只作为宿主展示这些数据。

本设计已确认以下边界：

- 新模块名为 `:album-api`，namespace 为 `com.github.sceneren.album.api`。
- 模块不应用 Compose 插件，不依赖 Compose、Material3、Coil 或任何 Composable。
- 模块允许依赖非 Compose 的 AndroidX Activity 与 Lifecycle，用于封装 `PickVisualMedia` 的注册、启动和生命周期协程。
- `PickVisualMedia` 每次选择一张图片；多次选择累积为分页列表。
- 选择成功后由模块自动取得持久只读 URI 权限并写入数据库。
- 列表与访问权限在进程结束和设备重启后仍可恢复。
- 旧 MediaStore 相册实现、XXPermissions 权限流、文件缓存路径转换和旧手动分页实现不保留。

## 目标

1. 提供无需 Compose 的图片选择入口，宿主只需注册一次并调用 `launch()`。
2. 保证成功回调前已取得持久 URI 权限并完成本地入库。
3. 使用 Room 保存模块管理的已选图片，并通过 Paging 3 提供稳定分页流。
4. 处理重复选择、授权失效、删除、清空和系统持久授权上限。
5. 用 `:app` 演示选择图片、分页网格展示、加载状态与错误重试。

## 非目标

- 不在 `:album-api` 中提供 Compose、View、Activity 页面或任何自定义 UI。
- 不查询整机 MediaStore，也不请求 `READ_MEDIA_IMAGES`、`READ_EXTERNAL_STORAGE` 或写存储权限。
- 不复制媒体到应用缓存，不暴露原始文件路径。
- 不提供视频、多选 Photo Picker、相册目录浏览、图片编辑或上传能力。
- 不保留旧 API 的源码兼容性；这是一次整体重构。

## 模块结构

```text
Album
├── album-api/                  # Android Library；选择、授权、Room、Paging
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   └── java/com/github/sceneren/album/api/
│       │       ├── AlbumApi.kt
│       │       ├── AlbumPhotoPickerLauncher.kt
│       │       ├── PhotoPickResult.kt
│       │       ├── PickedMedia.kt
│       │       └── internal/
│       │           ├── AlbumApiImpl.kt
│       │           ├── PickedMediaRepository.kt
│       │           ├── MediaMetadataReader.kt
│       │           ├── PersistedPermissionGateway.kt
│       │           └── db/
│       │               ├── AlbumDatabase.kt
│       │               ├── PickedMediaDao.kt
│       │               └── PickedMediaEntity.kt
│       └── test/
└── app/                        # Activity、ViewModel、Compose、Paging Compose、Coil
    └── src/main/java/com/github/sceneren/album/
        ├── MainActivity.kt
        ├── AlbumDemoViewModel.kt
        └── PickedMediaScreen.kt
```

主题文件与启动资源继续留在 `:app`。旧 `AlbumLoader`、`FileHelper`、`ImageDirectory`、`ImageItem`、`PagedResult`、旧 `AlbumViewModel` 和 `refresh` 包会被删除。

## 依赖边界

`:album-api` 使用：

- AndroidX Activity KTX：注册 `ActivityResultContracts.PickVisualMedia`。
- AndroidX Lifecycle Runtime KTX：在 Activity 生命周期协程中完成选择结果持久化。
- Room 2.8.4：结构化持久存储和查询失效通知。
- Paging 3.4.2 与 Room Paging：公开 `Flow<PagingData<PickedMedia>>`。
- Kotlin Coroutines Android 1.11.0：结构化并发和 IO 调度。
- KSP 2.3.10 与 Room Compiler 2.8.4：仅用于构建时代码生成。

`:app` 使用 Compose、Paging Compose 和 Coil，但这些依赖不会通过 `:album-api` 暴露。Gradle 使用 AGP 9.3.1 的内置 Kotlin 支持；`album-api` 不应用 Kotlin Android 插件或 Compose 插件。

## 公开 API

```kotlin
package com.github.sceneren.album.api

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

class AlbumApi private constructor(context: Context) {
    fun registerPhotoPicker(
        activity: ComponentActivity,
        onResult: (PhotoPickResult) -> Unit,
    ): AlbumPhotoPickerLauncher

    fun getPickedMedia(
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): Flow<PagingData<PickedMedia>>

    suspend fun remove(uri: Uri): Result<Boolean>

    suspend fun clear(): Result<Int>

    suspend fun reconcile(): Result<Int>

    companion object {
        const val DEFAULT_PAGE_SIZE: Int = 24

        @JvmStatic
        fun create(context: Context): AlbumApi
    }
}

fun interface AlbumPhotoPickerLauncher {
    fun launch()
}

data class PickedMedia(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val selectedAtEpochMillis: Long,
)

sealed interface PhotoPickResult {
    data class Selected(val media: PickedMedia) : PhotoPickResult
    data object Cancelled : PhotoPickResult
    data class Failed(
        val reason: PhotoPickFailureReason,
        val cause: Throwable,
    ) : PhotoPickResult
}

enum class PhotoPickFailureReason {
    PERSIST_PERMISSION_FAILED,
    UNSUPPORTED_MEDIA_TYPE,
    STORAGE_FAILED,
}

sealed class AlbumOperationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class PermissionReleaseFailed(
        val uri: Uri,
        cause: Throwable,
    ) : AlbumOperationException("Failed to release persisted access for $uri", cause)

    class StorageOperationFailed(
        cause: Throwable,
    ) : AlbumOperationException("Album storage operation failed", cause)

    class PartialClearFailed(
        val removedCount: Int,
        val failedUris: List<Uri>,
        cause: Throwable,
    ) : AlbumOperationException(
        "Cleared $removedCount items but failed to release ${failedUris.size} items",
        cause,
    )
}
```

`AlbumApi.create()` 始终保存 `applicationContext`。同一进程中所有 facade 共享同一个 Room 数据库实例，但 Activity Result 注册仍属于各自 Activity。

`registerPhotoPicker()` 必须在 Activity 进入 `STARTED` 前调用。实现会先检查生命周期状态，并在误用时抛出带明确说明的 `IllegalStateException`。返回的 launcher 只暴露无参数 `launch()`，内部固定构造 `PickVisualMediaRequest(PickVisualMedia.ImageOnly)`。

## Library Manifest

Library Manifest 不声明任何媒体或存储权限。它声明 Google Play services Photo Picker 回移模块所需的 `ModuleDependencies` service 和 `photopicker_activity:0:required` 元数据。宿主不需要重复声明，也不需要直接调用 Google Play services Module Install API。

AndroidX Activity Contract 按平台能力选择系统 Photo Picker、系统回移实现或 `ACTION_OPEN_DOCUMENT` 回退。所有路径都通过同一公开 launcher 使用。

## 数据模型与 Room Schema

内部表 `picked_media` 包含：

| 列 | 类型 | 规则 |
|---|---|---|
| `uri` | TEXT | 主键，保存完整 content URI 字符串 |
| `display_name` | TEXT | 非空；查询失败时使用 URI 安全回退名 |
| `mime_type` | TEXT | 非空；未知时保存 `image/*` |
| `size_bytes` | INTEGER | 可空；provider 未提供时为 null |
| `selected_at_epoch_millis` | INTEGER | 非空；用于最新选择优先排序 |
| `owns_persisted_grant` | INTEGER | 非空布尔值；控制删除时是否释放授权 |

DAO 以 `selected_at_epoch_millis DESC, uri ASC` 排序，确保时间相同时结果仍确定。URI 主键保证重复选择不会产生重复记录；重复选择会更新时间并移动到列表首位。

Room 数据库版本从 1 开始，并导出 schema 到模块的 schema 目录。后续 schema 变化必须提供显式 migration，不使用破坏性迁移。

## 选择与持久化数据流

1. `:app` 在 `MainActivity.onCreate()` 中调用 `registerPhotoPicker()`。
2. 用户点击宿主按钮，App 调用 `AlbumPhotoPickerLauncher.launch()`。
3. Contract 返回 `null` 时同步回调 `PhotoPickResult.Cancelled`。
4. Contract 返回 URI 时，模块在 Activity 的 `lifecycleScope` 中开始持久化。
5. 模块记录该 URI 是否已具有持久授权，以及数据库中是否已有模块记录。
6. 模块调用 `takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)`。
7. 模块在 IO 调度器读取 MIME、显示名和大小。已知 MIME 不是 `image/*` 时拒绝；缺失 MIME 或可选元数据时使用安全回退值。
8. Room 执行 upsert，写入最新选择时间。`owns_persisted_grant` 在模块首次取得新授权时为 true；已由宿主其他功能持有的授权不会被模块误释放。
9. 模块将数据库 URI 与 `ContentResolver.persistedUriPermissions` 对齐，删除系统已淘汰或撤销的记录。
10. 只有授权、元数据处理和入库全部完成后才在主线程回调 `PhotoPickResult.Selected`。
11. Room 表变化使现有 PagingSource 自动失效，App 收到刷新后的 PagingData。

若入库失败且该授权由本次操作新取得，模块释放该授权作为补偿。若授权在调用前已存在，则失败补偿不会释放它。

## 分页行为

`getPickedMedia(pageSize)` 要求 `pageSize > 0`，否则立即抛出 `IllegalArgumentException`。它创建：

- `PagingConfig.pageSize = pageSize`
- `initialLoadSize = pageSize`
- `prefetchDistance = maxOf(1, pageSize / 2)`
- `enablePlaceholders = false`

返回 Flow 在首次收集时先运行一次授权协调，再开始发射 Room Pager 数据。`:app` 在 ViewModel 中使用 `cachedIn(viewModelScope)`，Compose 层通过 Paging Compose 消费；数据库层只按请求页面读取，不把全部 URI 或元数据装入内存。

## 授权协调与 5,000 条上限

系统最多保存 5,000 个 Photo Picker 持久媒体授权。模块不导入宿主拥有但未写入 `picked_media` 的其他授权，也不会主动释放它们。

`reconcile()` 执行以下操作：

1. 读取当前包的 `persistedUriPermissions` 并仅保留具备读权限的 URI。
2. 与 `picked_media` 中模块已登记的 URI 比较。
3. 删除数据库中已经没有系统持久读授权的记录。
4. 返回删除记录数。

每次成功选择后和每个分页 Flow 首次收集前都会自动协调，因此系统淘汰旧授权后不会长期展示不可读记录。公开 `reconcile()` 允许宿主在从后台返回等时机主动触发，但正常展示不依赖宿主手动调用。

## 删除与清空

`remove(uri)` 在模块拥有该持久授权时先释放授权，再删除数据库记录；授权已不存在时直接删除失效记录。释放授权失败时保留数据库记录并返回失败，允许调用方重试。不存在记录时返回 `Result.success(false)`。

`clear()` 对所有模块记录执行相同行为。成功删除的数量作为结果返回。若部分 URI 无法释放，成功项仍被删除，失败项保留，并以 `AlbumOperationException.PartialClearFailed` 返回已删除数量和失败 URI，避免丢失可重试状态。单项释放失败使用 `PermissionReleaseFailed`，Room 操作失败使用 `StorageOperationFailed`。

权限、元数据、Room 写入、删除、清空和协调由内部 `Mutex` 串行化，避免并发选择与删除造成授权和数据库状态分叉。

## 错误处理

- 用户取消：`PhotoPickResult.Cancelled`，不改变数据库。
- `takePersistableUriPermission` 抛出异常：`PERSIST_PERMISSION_FAILED`，不写数据库。
- provider 明确返回非图片 MIME：释放本次新授权，回调 `UNSUPPORTED_MEDIA_TYPE`。
- 显示名、大小或 MIME 缺失：使用回退元数据，不把可访问图片误判为失败。
- Room 写入失败：补偿本次新授权并回调 `STORAGE_FAILED`。
- 分页查询失败：通过 Paging `LoadState.Error` 交给宿主展示和重试。
- 协程取消：继续传播 `CancellationException`，不转换为普通失败；已经取得但尚未入库的新授权会在 `finally` 中补偿。

`PhotoPickResult.Failed` 回调发生在主线程。模块不显示 Toast、Dialog 或日志 UI。

## 宿主演示

`:app` 的 `MainActivity`：

- 在 `onCreate()` 中创建 `AlbumApi` 并注册 launcher。
- Compose 按钮只调用 `launcher.launch()`。
- 将 `PhotoPickResult` 转为宿主自己的短期提示状态。

`AlbumDemoViewModel`：

- 创建同一数据库后端的 `AlbumApi` facade。
- 将 `getPickedMedia(24)` 使用 `cachedIn(viewModelScope)` 暴露给 UI。
- 提供删除和清空动作，但不持有 Activity 或 launcher。

`PickedMediaScreen`：

- 使用 Paging Compose 的 `LazyPagingItems` 和四列 `LazyVerticalGrid`。
- 使用 Coil 加载 content URI。
- 展示空态、首屏加载、追加加载、错误重试和无更多数据状态。
- 不请求任何媒体或存储运行时权限。

App Manifest 删除 `READ_EXTERNAL_STORAGE`、`WRITE_EXTERNAL_STORAGE`、`READ_MEDIA_IMAGES`、`READ_MEDIA_VIDEO`、`READ_MEDIA_VISUAL_USER_SELECTED` 和旧的手动模块安装声明；Library Manifest 会在合并时提供唯一的 Photo Picker 回移声明。

## 测试设计

### `:album-api` JVM 测试

通过 fake permission gateway、metadata reader 和 DAO 验证：

- 先取得持久授权，再执行数据库 upsert。
- 用户取消不调用授权或 DAO。
- 新授权入库失败时释放授权。
- 已存在授权入库失败时不误释放。
- 重复 URI 只保留一条并更新时间。
- 已知非图片 MIME 被拒绝。
- 缺失元数据使用回退值。
- reconcile 只删除模块记录中的失效 URI，不导入或释放宿主其他授权。
- remove/clear 的成功、缺失、授权释放失败和部分失败语义。
- 并发 retain/remove 被串行化。

### Room 与 Paging 测试

使用内存 Room 数据库和 Paging Testing 验证：

- 排序稳定且最新选择在前。
- 第 1 页、末页、空库和恰好整页的边界。
- pageSize 参数生效且非法值被拒绝。
- upsert、remove 和 reconcile 会使 PagingSource 失效。

### `:app` 测试

- ViewModel 对 Paging Flow 进行缓存并正确委托删除/清空。
- Compose 测试使用 fake PagingData 验证空态、图片项、加载、错误和重试。
- launcher 回调到宿主提示状态的映射不持有 Activity 到 ViewModel。

### 设备验证

系统 Photo Picker 与真实持久 URI 授权需要设备或模拟器验证：

1. 选择图片后立即展示。
2. 强制结束进程并重新打开后仍展示、仍可读取。
3. 重启设备后仍展示、仍可读取。
4. 重复选择同一图片不会重复，且移动到首位。
5. 删除和清空后授权与列表同步消失。
6. 在支持系统 Picker、回移 Picker 和 `ACTION_OPEN_DOCUMENT` 回退的设备路径上各验证一次。

## 构建与验证命令

```powershell
.\gradlew.bat :album-api:testDebugUnitTest
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
python .codex/scripts/gen_references.py
python .codex/scripts/gen_references.py --diff
```

前三个命令是本地完成门槛。`connectedDebugAndroidTest` 仅在设备或模拟器可用时运行；无法运行时必须明确报告，并保留上面的人工设备验证清单。

## 风险与约束

- Activity Result 注册有严格生命周期约束，因此公开 API 会在注册时主动校验状态并提供明确错误。
- 持久 URI 授权属于应用包而不是单个模块；`owns_persisted_grant` 用于避免释放模块接入前已经存在的授权，但宿主仍应统一通过本模块管理已选图片。
- 云媒体 URI 可能在读取时产生延迟；模块只读取轻量元数据，图片解码由 App 中的 Coil 按可见项完成。
- 系统可以因 5,000 条上限淘汰最旧授权；协调逻辑以系统授权为事实来源，不承诺保留被系统淘汰的媒体。
- 真正的跨重启访问只能通过设备验证证明，JVM 测试不能替代。

## 验收标准

1. Gradle 包含 `:album-api` Android Library，`:app` 依赖它。
2. `:album-api` 的 Gradle 依赖图不包含 Compose、Material3、Coil 或 Paging Compose。
3. 宿主可以注册 launcher 并仅用 `launch()` 启动 `PickVisualMedia.ImageOnly`。
4. `Selected` 回调发生前已经取得持久只读 URI 权限并完成 Room 入库。
5. 已选图片通过 Paging 3 分页，重复项去重并按最新选择时间倒序。
6. 首次收集分页流与每次选择后都会清理失效授权记录。
7. App 进程结束或设备重启后，仍受系统授权的图片会恢复显示和读取。
8. App 不声明宽泛媒体/存储权限，不再使用 XXPermissions 或手动 Google Play Module Install API。
9. App 只负责 Activity 注册、ViewModel 和 Compose 演示；旧 MediaStore 实现已移除。
10. 模块单元测试、App 单元测试和 Debug 构建通过；设备相关验证结果被明确记录。
