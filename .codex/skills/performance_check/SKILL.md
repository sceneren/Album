---
name: performance_check
description: Check Album project changes for performance, memory, ANR, and Android media handling risks.
---

# Album Performance Check

Use this skill when changes touch image loading, MediaStore queries, file caching, Compose lazy lists/grids, permissions, or startup.

## Media and IO

- MediaStore queries must run on `Dispatchers.IO`.
- Cursor usage must be wrapped in `use` or otherwise closed reliably.
- Large file copies must stream with bounded buffers and delete incomplete cache files on failure.
- Avoid loading full bitmaps in app code; prefer Coil `AsyncImage` and content URIs.
- Cache writes belong under app-owned cache directories and should have an explicit cleanup path.

## Compose Runtime

- Avoid expensive work directly inside composable bodies.
- Use `remember`, `rememberUpdatedState`, and `LaunchedEffect` keys deliberately for launchers, module clients, and auto-load triggers.
- Lazy grid/list items should have stable enough content to avoid avoidable recomposition and layout thrash.
- Avoid repeated load-more calls while `LoadMoreState.LOADING` or refresh animation is active.

## Startup and Permissions

- Keep startup work light in `Application.onCreate` and `MainActivity.onCreate`.
- Permission checks should not repeatedly prompt on every recomposition.
- Google Photo Picker module installation must retain a fallback path when Play services checks fail.

## Verification

- For non-device checks, run `./gradlew.bat :app:testDebugUnitTest`.
- For runtime behavior, use a device/emulator with image media and test API-level-specific permission paths.

