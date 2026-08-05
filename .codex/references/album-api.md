# Module: album-api

## Overview

`:album-api` is a reusable Android Library that exposes data and Activity Result integration for a permission-aware photo/video album. It contains no Compose, Material3, Coil, or other rendered UI.

## Metadata

| Item | Value |
|---|---|
| Type | Android Library (AAR) |
| Gradle path | `:album-api` |
| Namespace | `com.github.sceneren.album.api` |
| Min / compile SDK | 24 / 37 |
| Main source files | 25 |
| Compose / View Binding | disabled / disabled |

## Public API

- `AlbumApi.create(context)`: application-scoped facade.
- `getMediaAccessStatus(filter)`: returns `FULL`, `PARTIAL`, or `DENIED`.
- `syncPartialSelections(filter)`: 在 `PARTIAL` 状态下将当前筛选条件可访问的 MediaStore 媒体同步到持久列表，并返回同步记录数。
- `getMediaFeed(filter, bucketId, pageSize)`: returns source metadata and `Flow<PagingData<AlbumMedia>>`.
- `getMediaDirectories(filter)`: returns MediaStore bucket summaries only under full access.
- `registerPhotoPicker(activity, filter, maxSelectionCount, onResult)`: registers `PickVisualMedia` or `PickMultipleVisualMedia`; null count means no library-defined cap.
- `removePersistedSelection`, `clearPersistedSelections`, `reconcilePersistedSelections`: maintain stored selections and grants.
- Models: `AlbumMedia`, `AlbumDirectory`, `AlbumMediaFilter`, `AlbumMediaType`, `AlbumMediaSource`, `AlbumMediaFeed`, `PhotoPickResult`.

## Internal Data Flow

- Permission policy evaluates the requested media types by SDK level.
- Full access uses `AndroidMediaStoreDataSource` and `MediaStoreMediaPagingSource` with deterministic offset paging.
- Partial/denied access uses Room `picked_media`, ordered by selection order then URI.
- PARTIAL 刷新前通过 `syncPartialSelections` 将系统选定媒体写入同一 Room 表；系统记录不拥有可持久化 URI 授权。
- Picker processing deduplicates and validates the whole batch, retains new read grants, reads metadata, and commits one Room transaction. Failures roll back newly acquired grants best-effort.
- 相机拍摄成功后会立即写入同一持久列表；DENIED/PARTIAL 下可在后续会话中继续分页展示，已完成拍摄不会在取消当前会话时删除。
- Existing owned grants survive reselection; stale records can be reconciled; large URI queries are chunked below SQLite's legacy bind limit.
- Directory aggregation streams a lightweight cursor projection rather than materializing every full media model.

## Dependencies

- Public surface: AndroidX Activity KTX and Paging Runtime.
- Internal: AndroidX Core/Lifecycle, Room Runtime/Paging, Kotlin coroutines Android, KSP Room compiler.
- Tests: JUnit, Robolectric, AndroidX Test Core, Paging/Room test helpers, coroutine test.

## Boundary Rules

- Never add Compose/UI/rendering dependencies or app resources.
- Do not request runtime permissions; report access and let the host decide when to prompt.
- Keep URIs as the durable media identity and release only grants marked as library-owned.
