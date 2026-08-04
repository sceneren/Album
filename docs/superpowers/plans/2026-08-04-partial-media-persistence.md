# PARTIAL 媒体持久化实现计划

> **给执行代理：** 必须按任务逐项执行；每个任务先写失败测试，再实现最小改动并运行对应测试。所有新增/修改文档使用中文。当前按内联方式执行，不创建额外 worktree 或子代理。

**目标：** 将 Android PARTIAL 下可访问的系统媒体同步到 `:album-api` 的 Room 持久列表，并修复宿主按图片/视频筛选请求权限后刷新显示的问题。

**架构：** `AndroidMediaStoreDataSource.loadAll` 读取当前权限可见的全部媒体；`AlbumApi.syncPartialSelections` 在 PARTIAL 时把媒体转换成非库授权的 `PickedMediaDraft` 并批量 upsert，之后 `getMediaFeed` 继续从 Room 分页。`:app` 的 `AlbumViewModel.refresh` 先等待同步，再构造 feed，并取消旧 refresh 防止筛选切换时旧结果覆盖新结果。权限工厂继续传入当前筛选类型，并在 API 34+ 为对应类型附加选定媒体辅助权限。

**技术栈：** Kotlin、Android MediaStore、Room、AndroidX Paging、Kotlin Coroutines、JUnit/Robolectric。

## 全局约束

- `:album-api` 不得引入 Compose、UI、Material3、Coil 或应用资源。
- `FULL` 使用 MediaStore 分页；`PARTIAL`/`DENIED` 使用 Room 持久分页。
- 图片、视频和混合筛选必须在权限、MediaStore、Room 和 Photo Picker 中保持一致。
- 仅使用 `content://` URI；MediaStore/Room 工作运行在 IO dispatcher；取消异常继续抛出。
- 系统 PARTIAL 记录 `ownsPersistableGrant = false`；Photo Picker 已有库授权的 ownership 必须保留。
- 不修改工作区中用户已有的 `gradle/libs.versions.toml` 版本改动，也不提交 `.codex/scripts/__pycache__/`。

---

### 任务 1：为 MediaStore 增加“读取全部可见媒体”的数据源能力

**文件：**

- 修改：`album-api/src/main/java/com/github/sceneren/album/api/internal/mediastore/MediaStoreDataSource.kt`
- 修改：`album-api/src/main/java/com/github/sceneren/album/api/internal/mediastore/AndroidMediaStoreDataSource.kt`
- 测试：`album-api/src/test/java/com/github/sceneren/album/api/internal/mediastore/AndroidMediaStoreDataSourceTest.kt`
- 测试编译适配：`album-api/src/test/java/com/github/sceneren/album/api/AlbumApiTest.kt`

**接口：**

```kotlin
internal interface MediaStoreDataSource {
    suspend fun loadAll(mediaFilter: AlbumMediaFilter): List<AlbumMedia>
    suspend fun loadPage(...): List<AlbumMedia>
    suspend fun getDirectories(mediaFilter: AlbumMediaFilter): List<AlbumDirectory>
}
```

- [ ] **步骤 1：写失败测试。** 在 `AndroidMediaStoreDataSourceTest` 增加 `loadAllOmitsPagingArgumentsAndKeepsFilterSelection`：调用 `loadAll(AlbumMediaFilter.VIDEOS)`，断言返回的视频 URI/时长，API 30 provider 的 `QUERY_ARG_LIMIT` 与 `QUERY_ARG_OFFSET` 为未设置，排序为 `date_added DESC, _id DESC`，并断言 selection 只包含视频类型参数。给 `AlbumApiTest` 中 fake 增加临时 `loadAll` 空实现以保持测试可编译。
- [ ] **步骤 2：运行测试确认失败。**

  ```powershell
  .\gradlew.bat :album-api:testDebugUnitTest --tests "com.github.sceneren.album.api.internal.mediastore.AndroidMediaStoreDataSourceTest.loadAllOmitsPagingArgumentsAndKeepsFilterSelection" --console=plain
  ```

  预期：因 `MediaStoreDataSource.loadAll`/`AndroidMediaStoreDataSource.loadAll` 尚未实现而编译失败或测试失败。

- [ ] **步骤 3：实现最小改动。** 在接口增加 `loadAll`；在 Android 实现中用 `withContext(ioDispatcher)` 创建 `MediaStoreQuerySpec.create(mediaFilter, ALL_BUCKET_ID)`，调用已有 `query(spec, limit = null, offset = null)`。不要复制 cursor 映射逻辑，不要改变 `loadPage`。
- [ ] **步骤 4：运行测试确认通过。** 运行上面的单测命令，预期 PASS；再运行 `:album-api:testDebugUnitTest`，修复所有因 fake 接口扩展产生的编译错误。
- [ ] **步骤 5：提交。**

  ```powershell
  git add album-api/src/main/java/com/github/sceneren/album/api/internal/mediastore album-api/src/test/java/com/github/sceneren/album/api/internal/mediastore/AndroidMediaStoreDataSourceTest.kt album-api/src/test/java/com/github/sceneren/album/api/AlbumApiTest.kt
  git commit -m "feat: add full visible MediaStore loading"
  ```

### 任务 2：实现 AlbumApi 的 PARTIAL 同步与 Room 映射

**文件：**

- 修改：`album-api/src/main/java/com/github/sceneren/album/api/AlbumApi.kt`
- 测试：`album-api/src/test/java/com/github/sceneren/album/api/AlbumApiTest.kt`
- 测试：`album-api/src/test/java/com/github/sceneren/album/api/internal/database/RoomPickedMediaStoreTest.kt`

**接口：**

```kotlin
suspend fun AlbumApi.syncPartialSelections(
    mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
): Result<Int>
```

- [ ] **步骤 1：写失败测试。** 在 `AlbumApiTest` 增加三组断言：PARTIAL + `IMAGES_AND_VIDEOS` 调用 fake `loadAll`，写入两个 `PickedMediaDraft`，URI/媒体类型/元数据正确且 ownership 为 false，返回数量为 2；FULL 与 DENIED 返回 0 且 fake `loadAll` 调用数为 0。预置同 URI 且 `ownsPersistableGrant = true` 的 Room 行，再同步同 URI，断言 ownership 仍为 true。为 fake 记录 `loadAllCalls`、`lastLoadAllFilter` 和返回媒体。
- [ ] **步骤 2：运行测试确认失败。**

  ```powershell
  .\gradlew.bat :album-api:testDebugUnitTest --tests "com.github.sceneren.album.api.AlbumApiTest.partialSyncPersistsVisibleMedia" --console=plain
  ```

  预期：`syncPartialSelections` 尚不存在，测试编译失败。

- [ ] **步骤 3：实现最小改动。** 在 `AlbumApi` 增加 `syncPartialSelections`，通过 `resultOnIo` 解析状态；只有 PARTIAL 调用 `mediaStore.loadAll(mediaFilter)`。将每个 `AlbumMedia` 映射为 `PickedMediaDraft`：URI 为 `media.uri.toString()`，媒体类型使用 `media.mediaType.name`，复制显示名称/MIME/大小/宽高/时长，`selectedAtEpochMillis = System.currentTimeMillis()`，`ownsPersistableGrant = false`；批量调用 `pickedStore.upsertBatch` 并返回输入数量。不要调用 `grantManager.takeRead/releaseRead`。
- [ ] **步骤 4：加强 Room 回归测试并运行。** 在 `RoomPickedMediaStoreTest` 增加“PARTIAL 非 ownership upsert 不覆盖 Photo Picker ownership”的测试；运行 AlbumApi、Room 两组测试，预期 PASS。若现有 Room 实现已满足测试，不改变其事务和排序逻辑。
- [ ] **步骤 5：提交。**

  ```powershell
  git add album-api/src/main/java/com/github/sceneren/album/api/AlbumApi.kt album-api/src/test/java/com/github/sceneren/album/api/AlbumApiTest.kt album-api/src/test/java/com/github/sceneren/album/api/internal/database/RoomPickedMediaStoreTest.kt
  git commit -m "feat: persist partial media selections"
  ```

### 任务 3：让宿主刷新在 feed 前等待 PARTIAL 同步

**文件：**

- 修改：`app/src/main/java/com/github/sceneren/album/AlbumDataClient.kt`
- 修改：`app/src/main/java/com/github/sceneren/album/AlbumViewModel.kt`
- 测试：`app/src/test/java/com/github/sceneren/album/AlbumViewModelTest.kt`

**接口：**

```kotlin
internal interface AlbumDataClient {
    suspend fun syncPartialSelections(mediaFilter: AlbumMediaFilter): Result<Int>
    fun getFeed(mediaFilter: AlbumMediaFilter, bucketId: Long): AlbumMediaFeed
    suspend fun getDirectories(mediaFilter: AlbumMediaFilter): Result<List<AlbumDirectory>>
}
```

- [ ] **步骤 1：写失败测试。** 扩展 fake client，记录调用顺序并允许同步挂起；增加测试断言 `refresh` 的调用顺序为 `sync(filter)` 后 `feed(filter,bucket)`，同步失败仍会构造 feed 并保留错误信息；增加筛选快速切换测试，旧同步/旧 feed 完成后不能覆盖新筛选状态。所有测试使用现有 `MainDispatcherRule` 和 `advanceUntilIdle`。
- [ ] **步骤 2：运行测试确认失败。**

  ```powershell
  .\gradlew.bat :app:testDebugUnitTest --tests "com.github.sceneren.album.AlbumViewModelTest.partialRefreshSyncsBeforeFeed" --console=plain
  ```

  预期：`AlbumDataClient` 尚无同步方法，或当前同步调用顺序断言失败。

- [ ] **步骤 3：实现最小改动。** 在 `AlbumApiDataClient` 转发 `api.syncPartialSelections`。在 ViewModel 中新增 `refreshJob`，`refresh()` 捕获当前 filter/bucket，取消旧 refresh 与 directory 任务，并在 `viewModelScope.launch` 中先调用同步，再获取 feed。同步失败先记录错误但继续获取 feed；feed 发布前检查当前 UI filter/bucket 仍与捕获值匹配。发布 feed 后按现有规则清空/保留目录，FULL 时启动目录任务，并在目录回调中再次检查 filter/source。
- [ ] **步骤 4：运行宿主测试。** 运行 `:app:testDebugUnitTest`，预期现有筛选、目录重置和新增竞态测试全部 PASS。
- [ ] **步骤 5：提交。**

  ```powershell
  git add app/src/main/java/com/github/sceneren/album/AlbumDataClient.kt app/src/main/java/com/github/sceneren/album/AlbumViewModel.kt app/src/test/java/com/github/sceneren/album/AlbumViewModelTest.kt
  git commit -m "fix: sync partial media before refreshing feed"
  ```

### 任务 4：锁定按媒体类型请求权限的回归行为

**文件：**

- 修改：`app/src/test/java/com/github/sceneren/album/MediaPermissionRequestFactoryTest.kt`
- 视测试结果修改：`app/src/main/java/com/github/sceneren/album/MediaPermissionRequestFactory.kt`
- 视测试结果修改：`app/src/main/java/com/github/sceneren/album/MainActivity.kt`

- [ ] **步骤 1：写回归测试。** 增加 API 34 仅图片期望 `[READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED]`、仅视频期望 `[READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED]`；保留混合和 API 32 测试。测试 `MainActivity` 无需新增 UI，只确认权限工厂接收当前筛选类型。
- [ ] **步骤 2：运行测试确认当前行为。**

  ```powershell
  .\gradlew.bat :app:testDebugUnitTest --tests "com.github.sceneren.album.MediaPermissionRequestFactoryTest" --console=plain
  ```

  预期：若现有实现已满足这些断言，测试直接 PASS；若失败，只修改权限数组生成或调用处，不移除 API 34 选定媒体辅助权限。

- [ ] **步骤 3：运行完整宿主单测。**

  ```powershell
  .\gradlew.bat :app:testDebugUnitTest --console=plain
  ```

- [ ] **步骤 4：提交（仅当本任务产生代码/测试差异）。**

  ```powershell
  git add app/src/main/java/com/github/sceneren/album/MainActivity.kt app/src/main/java/com/github/sceneren/album/MediaPermissionRequestFactory.kt app/src/test/java/com/github/sceneren/album/MediaPermissionRequestFactoryTest.kt
  git commit -m "test: lock filter-specific media permissions"
  ```

### 任务 5：全量验证、引用检查与交付

**文件：**

- 检查：`:album-api` 与 `:app` 所有本次变更文件
- 检查：`docs/superpowers/specs/2026-08-04-partial-media-persistence-design.md`
- 必要时修改：`.codex/references/album-api.md`（新增公共 API 时使用中文）

- [ ] **步骤 1：运行模块单测、构建和 lint。**

  ```powershell
  .\gradlew.bat :album-api:testDebugUnitTest :app:testDebugUnitTest :album-api:lintDebug :app:lintDebug :album-api:assembleDebug :app:assembleDebug --max-workers=1 --console=plain
  ```

- [ ] **步骤 2：检查变更范围。** 使用 `git diff --check`、`git status --short` 和 `git diff --stat`，确认不包含用户的 `gradle/libs.versions.toml` 改动或 `.codex/scripts/__pycache__/`，并确认 `:album-api` 没有 Compose/UI 依赖。
- [ ] **步骤 3：按项目规则更新引用。** 若公共 API 变更导致引用文件过时，只更新 `.codex/references/album-api.md` 中对应内容，全文新增说明使用中文；不改动无关引用扫描结果。
- [ ] **步骤 4：运行最终测试并记录结果。** 重跑失败过的聚焦测试和全量模块测试；如果连接设备不可用，不将其伪装成通过，并在交付说明中明确未执行的设备验证。
- [ ] **步骤 5：提交最终变更。** 仅暂存本任务文件，保留用户已有工作区改动；提交信息使用中文或英文均可，文档内容保持中文。

