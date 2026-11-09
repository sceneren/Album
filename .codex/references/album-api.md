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
| Main source files | 66 |
| Compose / View Binding | disabled / disabled |

## Public API

- `AlbumApi.create(context)`: application-scoped facade.
- `getMediaAccessStatus(filter)`: returns `FULL`, `PARTIAL`, or `DENIED`.
- `syncPartialSelections(filter)`: 在 `PARTIAL` 状态下整理已失效的系统持久 URI 授权。
- `getMediaFeed(filter, bucketId, pageSize)`: returns source metadata and `Flow<PagingData<AlbumMedia>>`.
- `getMediaDirectories(filter)`: returns MediaStore bucket summaries only under full access.
- `registerPhotoPicker(activity, filter, maxSelectionCount, onResult)`: registers `PickVisualMedia` or `PickMultipleVisualMedia`; null count means no library-defined cap.
- `AlbumCameraPermissionRequestFactory.shouldRequest(context)`: returns true only when the merged Manifest declares `CAMERA` and it is not granted; the UI host then requests it before launching the system camera.
- `removePersistedSelection`, `clearPersistedSelections`, `reconcilePersistedSelections`: maintain system-persisted URI grants and camera captures.
- Models: `AlbumMedia`, `AlbumDirectory`, `AlbumMediaFilter`, `AlbumMediaType`, `AlbumMediaSource`, `AlbumMediaFeed`, `PhotoPickResult`.

## Internal Data Flow

- Permission policy evaluates the requested media types by SDK level.
- Full access uses `AndroidMediaStoreDataSource` and `MediaStoreMediaPagingSource` with deterministic offset paging. Both paging sources use start offsets for refresh/append and exclusive end offsets for prepend; prepend clamps its length at zero, and `itemsBefore` records the exact refresh offset even for a short tail page. `OffsetPagingRegressionTest` covers tail refresh, variable-sized prepends and URI uniqueness.
- Partial access combines system-authorized `MediaStore` pages with `ContentResolver.persistedUriPermissions` and completed camera captures via `AuthorizedMediaPagingSource`, without any local Room database.
- Denied access pages directly from `ContentResolver.persistedUriPermissions` and completed camera captures via `AuthorizedMediaPagingSource`, without opening historical media content streams. Explicit reconciliation still verifies readability.
- Picker processing deduplicates and validates the whole batch, retains new read grants via `ContentResolver.takePersistableUriPermission`, and reads metadata. Failures roll back newly acquired grants best-effort.
- 相机拍摄成功后保存在应用专属 `camera` 目录并在会话中复用，确认时不会二次复制到 `photo_picker` 目录；DENIED/PARTIAL 下可在后续会话中继续分页展示。
- Directory aggregation streams a lightweight cursor projection rather than materializing every full media model.

## Dependencies

- Public surface: AndroidX Activity KTX and Paging Runtime.
- Internal: AndroidX Core/Lifecycle, Kotlin coroutines Android, Luban image compression.
- Tests: JUnit, Robolectric, AndroidX Test Core, Paging test helpers, coroutine test.

## Boundary Rules

- Never add Compose/UI/rendering dependencies or app resources.
- Do not request runtime permissions; report access and let the host decide when to prompt.
- Keep URIs as the durable media identity and release only grants marked as library-owned.

## Result and format handling

- Image materialization enforces a 256 MiB limit before and during streaming copies; partial files are removed on failure/cancellation, completed shared caches are retained.
- Special-format resolution uses provider codes or XMP rather than motion-photo filename prefixes. Picker metadata fallback reads at most 1 MiB for JPEG/HEIC/HEIF.
