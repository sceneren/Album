# PARTIAL 媒体持久化设计

## 目标

让 Android 的部分媒体权限能够接入现有的 `:album-api` 数据流：宿主针对当前筛选类型请求权限后，将 PARTIAL 状态下可访问的媒体同步到 Room，并通过与 Photo Picker 选择相同的持久分页列表展示。同时保持图片/视频权限按筛选类型请求，并保留由库取得的 Photo Picker 持久授权。

## 范围与约束

- `:album-api` 继续是可复用的 Android Library，不引入 Compose、UI、Material3、Coil 或应用资源。
- `:album-api` 负责 MediaStore 查询、Room 持久化、访问状态路由和 Photo Picker 授权记录；`:app` 只负责权限请求、生命周期/UI 状态和渲染。
- `FULL` 继续使用 MediaStore 分页和 MediaStore 文件夹聚合。
- `PARTIAL` 与 `DENIED` 继续使用 `picked_media` 持久分页源；PARTIAL 在构造 feed 前将当前系统可访问媒体追加到该表。
- `IMAGES`、`VIDEOS`、`IMAGES_AND_VIDEOS` 必须原样贯穿权限解析、Photo Picker 合约、PARTIAL 同步查询、Room 过滤和 MediaStore 查询。
- 使用 URI 字符串作为持久媒体身份，不新增原始文件路径或废弃的 `DATA` 列。

## 架构

### Library 公共 API

在 `AlbumApi` 增加以下挂起操作：

```kotlin
suspend fun syncPartialSelections(
    mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
): Result<Int>
```

该操作先解析指定筛选条件的访问状态。状态为 `PARTIAL` 时，从 MediaStore 读取当前可访问且匹配筛选条件的全部图片/视频，将每项转换为 `PickedMediaDraft`，批量 upsert 到 Room，并返回提交的记录数。状态为 `FULL` 或 `DENIED` 时不查询 MediaStore，直接返回 `Result.success(0)`。异常包装为 `Result.failure`，协程取消继续抛出。

系统 PARTIAL 记录写入时设置 `ownsPersistableGrant = false`：其访问权由系统的选定媒体权限控制，库不能为它调用 `takePersistableUriPermission`，也不能释放并非库取得的授权。已有 Photo Picker 记录保留现有 ownership 标记。`picked_media` 的 URI 唯一约束负责去重；同一 URI 先由 Photo Picker 选择、后在 PARTIAL 中可见时，upsert 必须保留原有 ownership 标记。

### MediaStore 数据源

为内部 `MediaStoreDataSource` 增加非分页的 `loadAll(mediaFilter)`。`AndroidMediaStoreDataSource` 复用已有 projection、筛选条件、确定性排序（`date_added DESC, _id DESC`）、游标关闭和媒体类型过滤逻辑，但不添加 `LIMIT/OFFSET`。该操作继续运行在配置的 IO dispatcher 上；现有 FULL 分页的 `loadPage` 不变。

### Room 映射

将 `loadAll` 返回的 `AlbumMedia` 转换为 `PickedMediaDraft`，沿用 content URI、媒体类型、显示名称、MIME 类型、大小、宽高和时长。`selectedAtEpochMillis` 使用本次同步时间，`ownsPersistableGrant` 固定为 `false`。通过现有的 `PickedMediaStore.upsertBatch` 写入，不新增表或数据库迁移。现有 URI 主键/唯一键与 ownership-preserving upsert 作为系统 PARTIAL 媒体和 Photo Picker 媒体的合并边界。

### 宿主刷新顺序

为 `AlbumDataClient` 增加 `syncPartialSelections`，并将 `AlbumViewModel.refresh()` 改为可取消的 `viewModelScope` 任务：

1. 捕获当前筛选类型和文件夹 ID。
2. 取消上一次 refresh/directory 任务。
3. 等待 `client.syncPartialSelections(filter)` 完成后再获取 feed。
4. 调用 `client.getFeed(filter, bucket)`，发布 source、访问状态和 Paging flow。
5. 仅当 feed 为 FULL MediaStore 源时加载文件夹列表。

任务发布异步结果前检查最新筛选类型和 source，避免快速切换筛选时旧 feed 覆盖新 feed。同步失败通过现有 `errorMessage` 展示，但仍继续构造 feed，使已有 Photo Picker 记录在系统查询暂时失败时仍可见。同步成功清除之前的错误；目录查询错误维持现有行为。

### 权限请求行为

宿主权限工厂保持按筛选类型生成：

- `IMAGES`：API 33+ 请求 `READ_MEDIA_IMAGES`，API 34+ 追加 `READ_MEDIA_VISUAL_USER_SELECTED`。
- `VIDEOS`：API 33+ 请求 `READ_MEDIA_VIDEO`，API 34+ 追加 `READ_MEDIA_VISUAL_USER_SELECTED`。
- `IMAGES_AND_VIDEOS`：请求两个媒体权限，API 34+ 追加选定媒体辅助权限。
- API 32 及以下：请求 `READ_EXTERNAL_STORAGE`。

API 34+ 的选定媒体辅助权限必须与对应媒体类型权限一起请求，才能让 Android 报告 PARTIAL；它不是额外的媒体类别。权限回调和 onResume 都把当前筛选类型传入 `AlbumApi`。

## 错误处理与生命周期

- 所有 MediaStore、Room 和授权操作都通过库现有的 `resultOnIo` 边界及数据源 dispatcher 在 `Dispatchers.IO` 上执行。
- `CancellationException` 不转换为失败的 `Result`。
- PARTIAL 查询失败或返回空列表时不删除已有持久选择；系统记录等待下一次成功同步，现有显式 reconcile 仍只管理 Photo Picker 持久授权。
- 批量 upsert 使用单次数据库事务，使重复 URI 收敛为确定结果。
- 宿主 refresh 被新任务取代时取消旧任务；只有最新捕获的筛选类型和文件夹才能更新 UI 状态。

## 测试

1. `MediaStoreDataSource` fake 和 Android 数据源测试覆盖 `loadAll`、筛选条件传递、确定性排序以及图片/视频游标映射。
2. `AlbumApiTest` 验证 PARTIAL 调用 `loadAll` 并写入非 ownership draft，返回记录数；FULL/DENIED 不查询；重复 URI upsert 保留已有 Photo Picker ownership 标记。
3. `RoomPickedMediaStoreTest` 验证系统记录和 Photo Picker 记录按 URI 去重，并保留 `ownsPersistableGrant`。
4. `AlbumViewModelTest` 验证 refresh 在构造 feed 前等待 PARTIAL 同步；同步失败仍暴露已持久化数据；筛选切换后忽略旧结果。
5. `MediaPermissionRequestFactoryTest` 验证仅图片、仅视频、混合媒体和旧版 SDK 的权限数组，包括 API 34 选定媒体辅助权限。

## 非目标

- 推断 Photo Picker 虚拟选择项所在的文件夹名称。
- 为 PARTIAL Room 记录增加现有媒体模型之外的文件夹数据。
- 将运行时权限请求移入 `:album-api`。
- 改变现有 Photo Picker 最大选择数量或授权清理语义。
