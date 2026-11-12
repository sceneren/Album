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
| Main source files | 73 |
| Compose / View Binding | disabled / disabled |

## Public API

- `AlbumApi.create(context)`: application-scoped facade.
- `AlbumPickerClient.openSessionAsync` / `snapshotAsync`: IO-backed session creation, pruning and reads. Synchronous compatibility methods are WorkerThread-only. All writes/confirmation/cancellation serialize by session ID across clients through reference-counted process-wide locks; cleanup skips in-flight sessions.
- Picker and camera results expose URIs, never public file paths. Uncompressed media returns the source URI without materialization; only compressed images create private output.
- `releaseGeneratedMedia(context, result)` idempotently releases durable compressed-output leases after consumers finish. Explicit generated deletion accepts an owned FileProvider URI.
- `getMediaAccessStatus(filter)`: returns `FULL`, `PARTIAL`, or `DENIED`.
- `syncPartialSelections(filter)`: compatibility entry point; paging now queries system-authorized resources directly without eagerly opening historical media.
- `observeMediaChanges()` / `createMediaChangeTracker(filter)`: batched lifecycle notifications and an IO change baseline. Unchanged resumes do not refresh; only edited authorized URIs lose metadata caches. Explicit `invalidateMediaCache()` remains available for maintenance.
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
- `ConsistentMediaPagingSource` checks signatures before/after every page; a mutation invalidates the generation before it can mix offsets. Refresh relocates the retained loaded window by surviving URIs. Partial feeds translate deduplicated offsets through identity-only MediaStore positions. `MediaMutationPagingTest` covers insertions, deletions, replaced anchors and in-flight mutations for both sources.
- `AlbumMedia.generationModified` propagates through picker metadata and session persistence to host thumbnail/preview cache keys. Full-access API 30+ signatures reuse volume version/generation checkpoints. Partial/older hashes use one application-lifetime change observer and a 30-second time window (one second when observation is unavailable). Foreground checks force validation for missed notifications; page-end checks ignore time expiry alone while still checking revisions/generations, avoiding a second scan for a slow unchanged page. Camera signatures inspect attributes without decoding.
- Each authorized Paging generation captures its loading grant/camera snapshot once and checks current signatures before and after loading. Scanning preceding offsets reuses metadata without allocating media models outside the requested page. `AndroidMediaChangeObserver` coalesces provider notifications and unregisters on cancellation; foreground checks prioritize visible/selected URIs and revalidate at most 200 cached items without content streams or a shared long paging lock. Cached URIs outside the window are revalidated when returned by a page. Changed URIs are invalidated under a generation guard so old reads cannot repopulate stale entries. Empty metadata cursors and revoked access exclude deleted/unreadable media even when MIME/grants remain.
- Deleted persisted URIs can lose MIME metadata while retaining a system grant. `ContentResolverUriMetadataReader` now confirms existence through MIME/metadata cursors before classifying missing MIME, and probes a descriptor only for ambiguous metadata without reading bytes. Known unavailable URIs are revalidated instead of trusting residual MIME, allowing restored files to return. Temporary provider failures retain metadata and the change baseline. `AuthorizedMediaDeletionTest` exercises the real reader with provider deletion, restoration and transient failures on API 29/34.
- Picker processing deduplicates and validates the whole batch, retains new read grants via `ContentResolver.takePersistableUriPermission`, and reads metadata. Failures roll back newly acquired grants best-effort.
- Camera originals remain under `camera` and return URIs directly. `CompletedCameraMediaSource` lists file attributes, probes only returned page items, and caches at most 128 metadata records by path/size/millisecond modification time. Camera originals never participate in automatic LRU eviction.
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

- The original-copy materializer is removed. `AlbumUriImageCompressor` uses Luban URI decoding, target calculation and native JPEG encoding; checks quota before writing and atomically publishes a UUID-named compressed file. Source names never become output paths.
- `AlbumGeneratedMediaCache` budgets half the currently available storage per compressed batch and evicts unused outputs/legacy copies by LRU. Current files and durable unreleased leases are protected across clients/process recreation. Release updates last-use time; failure/cancellation cleans unfinished/unpublished output. Oversized compressed output has a typed exception shown as “选择的文件太大” by both UIs.
- Special-format tags use only case-insensitive filename extensions: `.livp` maps to MOTION_PHOTO and `.gif` to GIF. Ambiguous image/video extensions and missing names remain NONE; neither Photo Picker nor MediaStore queries special-format/XMP columns or probes image content for tags.
