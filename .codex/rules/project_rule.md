# Project Rule: Album

## 1. Required Reading

- Before modifying code, read this file, `.codex/references/_scan.json`, and the relevant module reference.
- Use `.codex/references/app.md` for host UI work and `.codex/references/album-api.md` for media data work.
- Do not invent modules, public APIs, Gradle aliases, permissions, or resources; inspect the repository first.

## 2. Project Facts

- Android project using Gradle Kotlin DSL and Kotlin; min SDK 24, target/compile SDK 37.
- Modules: `:app` (Compose Material3 host demo) and `:album-api` (reusable Android Library).
- `:app` depends on `:album-api`; `:album-api` must never depend on `:app`.
- App package/application id: `com.github.sceneren.album`; library namespace: `com.github.sceneren.album.api`.
- Main entry: `app/src/main/java/com/github/sceneren/album/MainActivity.kt`.

## 3. Module Boundaries

- `:album-api` owns public media models, permission-state resolution, Photo Picker registration and result persistence, Room storage, MediaStore querying, directory aggregation, and Paging sources.
- `:album-api` is data-only. Do not enable Compose or add Compose UI, Material3, Coil, or app resources to it.
- Activity Result types required to register `PickVisualMedia` are part of the library's public Android integration boundary; rendered UI remains in `:app`.
- `:app` owns permission prompts, lifecycle/UI state, filter and directory controls, media rendering, and host wiring only.
- UI consumes immutable `StateFlow` with lifecycle-aware collection.

## 4. Media and Permission Rules

- `FULL` media access routes paging and directory queries to MediaStore.
- `PARTIAL` and `DENIED` route paging to the persisted Photo Picker selection list.
- Apply `IMAGES`, `VIDEOS`, and `IMAGES_AND_VIDEOS` consistently to permission resolution, Photo Picker contracts, persisted paging, and MediaStore queries.
- Keep API 30+ Bundle paging and API 24-29 `LIMIT/OFFSET` behavior aligned and deterministic (`date_added DESC, _id DESC`).
- Use `content://` URIs; never query deprecated raw-data columns or copy media merely for display.
- Run resolver, Room, and grant operations off the main thread; always close cursors with `use`.
- Retain picker read access when available, record grant ownership, and release only grants owned by this library.
- The host owns manifest/runtime media permissions. Legacy read permission is capped at API 32; API 33+ uses `READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO`; API 34+ recognizes selected-media access.

## 5. Forbidden Patterns

| Pattern | Use Instead |
|---|---|
| Compose/UI dependency or composable in `:album-api` | Public data/API contracts and Activity Result integration |
| MediaStore/Room work on the main thread | `Dispatchers.IO` / suspend APIs |
| Raw external paths or `MediaStore.*.DATA` | `content://` URI and `ContentResolver` |
| Manual bitmap decoding in gallery cells | Coil in `:app` |
| Offset keys based only on item count or unstable sort | Explicit offset plus deterministic secondary `_id` order |
| Repeated permission prompts from recomposition | Host Activity Result launcher triggered by user action |
| Hardcoded feature colors or product text | `MaterialTheme` and Android string resources |

## 6. Build and Test

- Library unit tests: `./gradlew.bat :album-api:testDebugUnitTest`.
- App unit tests: `./gradlew.bat :app:testDebugUnitTest`.
- Debug artifacts: `./gradlew.bat :album-api:assembleDebug :app:assembleDebug`.
- Lint: `./gradlew.bat :album-api:lintDebug :app:lintDebug`.
- Instrumented tests require a device/emulator: `./gradlew.bat :app:connectedDebugAndroidTest`.
- Add focused tests for access routing, filter parity, paging keys, cursor queries, grant rollback/cleanup, and persisted ordering.
