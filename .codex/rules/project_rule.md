# Project Rule: Album

## 1. Required Reading

- Before modifying code, read this file and the relevant document under `.codex/references/`.
- This project uses full references mode. Use `.codex/references/_scan.json` to locate modules and files.
- Do not invent modules, classes, APIs, Gradle aliases, permissions, or resources that are not present in the repository.
- CodeGraph CLI exists on this machine, but the current project initialization uses full references mode because CLI exploration was not compatible with the expected command shape.

## 2. Project Facts

- Platform: Android application, Gradle Kotlin DSL.
- Root project: `Album`.
- Module: `:app`.
- Package, namespace, and application id: `com.github.sceneren.album`.
- Language and UI: Kotlin with Jetpack Compose Material3.
- SDK: min 24, target 37, compile 37.
- Main entry: `app/src/main/java/com/github/sceneren/album/MainActivity.kt`.
- App logic centers on MediaStore image querying, Android photo permissions, Google Photo Picker availability, Coil image loading, and Compose lazy grid pagination.

## 3. Architecture Rules

- Keep the current single-module structure unless the user explicitly asks for modularization.
- UI belongs in Compose functions and `MainActivity`; state and pagination orchestration belong in `AlbumViewModel`.
- Device media queries belong in `AlbumLoader`; file-copy/cache path conversion belongs in `FileHelper`.
- MediaStore, file copy, and cache cleanup work must run on `Dispatchers.IO`.
- UI should consume immutable state from `StateFlow` via lifecycle-aware collection.
- New reusable scrolling or refresh behavior should live under `com.github.sceneren.album.refresh`.
- New theme tokens should live under `com.github.sceneren.album.ui.theme`.

## 4. Android and Media Rules

- Request image/media permissions through the existing XXPermissions flow or a deliberately chosen Activity Result API.
- Preserve SDK-gated storage behavior: `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` are capped at API 32; Android 13+ media permissions use `READ_MEDIA_*`.
- Prefer `content://` URIs for display and processing. Do not add code that relies on deprecated `MediaStore.Images.Media.DATA`.
- If a real file path is needed, use or extend `FileHelper` so content is copied into app-owned cache.
- Keep `AlbumLoader.init(context)` and `FileHelper.init(context)` before their APIs are used. Prefer application context storage.
- Photo Picker integration must keep the Google Play services module fallback path.

## 5. Forbidden Patterns

| Pattern | Use Instead | Reason |
|---|---|---|
| MediaStore query or file copy on the main thread | `withContext(Dispatchers.IO)` | Prevent ANR and jank |
| `MediaStore.Images.Media.DATA` or raw external paths | `content://` URI, `ContentResolver`, `FileHelper` cache | Scoped storage compatibility |
| Manual bitmap decoding in Compose list cells | Coil `AsyncImage` | Avoid memory spikes and duplicate image pipeline |
| Direct mutable UI state as the source of truth for media lists | `AlbumViewModel` + `StateFlow` | Stable lifecycle-aware state |
| Adding broad storage permissions without SDK caps | Existing Manifest permission pattern | Android policy and runtime compatibility |
| Adding more hardcoded Compose colors in feature UI | `MaterialTheme.colorScheme` or theme tokens | Current code already has hardcoded demo colors; avoid spreading them |
| Blocking sleeps or busy waits | Coroutine suspension and callbacks | Keep UI responsive |

## 6. Naming and Layout

- Activity classes end with `Activity`; ViewModel classes end with `ViewModel`.
- Immutable model data classes use descriptive nouns: `ImageItem`, `ImageDirectory`, `PagedResult`.
- Compose functions use PascalCase and should describe the rendered UI or reusable behavior.
- Package names remain under `com.github.sceneren.album`.
- Resource names use lowercase snake_case. User-facing strings should move to `strings.xml` when they become product text rather than demo/debug text.

## 7. Build and Test

- Debug build on Windows: `./gradlew.bat :app:assembleDebug`.
- Unit tests on Windows: `./gradlew.bat :app:testDebugUnitTest`.
- Instrumented tests require an Android device or emulator: `./gradlew.bat :app:connectedDebugAndroidTest`.
- Meaningful tests should be added when changing pagination, permission flow, `AlbumLoader`, `FileHelper`, or reusable refresh components.

