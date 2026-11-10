# Project Rule: Album

## 1. Required Reading

- Before modifying code, read this file, `.codex/references/_scan.json`, and the relevant module reference.
- Use `.codex/references/app.md` for host UI work and `.codex/references/album-api.md` for media data work.
- Do not invent modules, public APIs, Gradle aliases, permissions, or resources; inspect the repository first.

## 2. Project Facts

- Android project using Gradle Kotlin DSL and Kotlin; min SDK 24, target/compile SDK 37.
- Modules: `:app` (host demo), `:album-api` (data-only Android Library), `:album-ui-view` (View picker UI library), `:album-ui-compose` (Compose picker UI library), `:album-video-api` (lightweight playback contract), and optional `:album-video-media3` (Media3 playback engine).
- `:app` depends on `:album-api`, `:album-ui-view`, `:album-ui-compose`, and optional `:album-video-media3`; both UI libraries depend on `:album-api` and `:album-video-api`, never Media3. `:album-api` must never depend on UI/video modules or `:app`. `:album-video-media3` depends only on `:album-video-api` and Media3, not either picker UI or the data API.
- App package/application id: `com.github.sceneren.photopicker`; library namespaces: `com.github.sceneren.album.api`, `com.github.sceneren.album.ui.view`, `com.github.sceneren.album.ui.compose`.
- Main entry: `app/src/main/java/com/github/sceneren/photopicker/MainActivity.kt`.

## 3. Module Boundaries

- `:album-api` owns public media models, permission-state resolution, Photo Picker registration and system grant management (`ContentResolver.persistedUriPermissions`), MediaStore querying, directory aggregation, Paging sources, session persistence, camera capture, and file materialization/compression.
- `:album-api` is data-only. Do not enable Compose or add Compose UI, Material3, Coil, or app resources to it.
- `:album-ui-view` and `:album-ui-compose` provide reusable picker UI without depending on Coil; image loading is delegated via `AlbumImageLoader`.
- `:app` owns demo UI, Coil `ImageLoader` wiring, optional `AlbumVideoPlayerFactory` registration, and Manifest declarations.
- `:album-video-api` owns only the main-thread/asynchronous-decoding playback contract and rendering View; no Media3/Compose/Coil dependency. `:album-video-media3` is opt-in. A missing UI factory must fall back to static `VIDEO_COVER`, without controls or a decoder.

## 4. Media and Permission Rules

- `FULL` media access routes paging and directory queries to MediaStore.
- `PARTIAL` routes paging to system-authorized MediaStore combined with `ContentResolver.persistedUriPermissions` and completed camera captures.
- `DENIED` routes paging to `ContentResolver.persistedUriPermissions` and completed camera captures.
- Apply `IMAGES`, `VIDEOS`, and `IMAGES_AND_VIDEOS` consistently to permission resolution, Photo Picker contracts, authorized paging, and MediaStore queries.
- Keep API 30+ Bundle paging and API 24-29 `LIMIT/OFFSET` behavior aligned and deterministic (`date_added DESC, _id DESC`).
- Use `content://` URIs; never query deprecated raw-data columns or copy media merely for display.
- Run resolver, file materialization, and grant operations off the main thread; always close cursors with `use`.
- Retain picker read access via `ContentResolver.takePersistableUriPermission` when available.
- The host owns manifest/runtime media permissions. Legacy read permission is capped at API 32; API 33+ uses `READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO`; API 34+ recognizes selected-media access.

## 5. Forbidden Patterns

| Pattern | Use Instead |
|---|---|
| Compose/UI dependency or composable in `:album-api` | Public data/API contracts and Activity Result integration |
| MediaStore/ContentResolver/file-copy work on the main thread | `Dispatchers.IO` / suspend APIs |
| Synchronous `SharedPreferences.commit()` on the main thread | `preferences.edit { ... }` (`apply()`) or `Dispatchers.IO` |
| Raw external paths or `MediaStore.*.DATA` | `content://` URI and `ContentResolver` |
| Manual bitmap decoding in gallery cells | Host `AlbumImageLoader` (Coil in `:app`) |
| Offset keys based only on item count or unstable sort | Explicit offset plus deterministic secondary `_id` order |
| Repeated permission prompts from recomposition | Host Activity Result launcher triggered by user action |
| Hardcoded feature colors or product text | Theme tokens / `AlbumPickerAppearance` and Android string resources |

## 6. Build and Test

- Library unit tests: `./gradlew.bat :album-api:testDebugUnitTest`.
- App unit tests: `./gradlew.bat :app:testDebugUnitTest`.
- Debug artifacts: `./gradlew.bat :album-api:assembleDebug :app:assembleDebug`.
- Lint: `./gradlew.bat :album-api:lintDebug :app:lintDebug`.
- Instrumented tests require a device/emulator: `./gradlew.bat :app:connectedDebugAndroidTest`.
- Add focused tests for access routing, filter parity, paging keys, cursor queries, grant rollback/cleanup, and persisted ordering.
