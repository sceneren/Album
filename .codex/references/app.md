# Module: app

## Overview

`:app` is the Android application and host demonstration for `:album-api`. It owns Compose UI, runtime permission requests, Activity lifecycle wiring, and Coil rendering. It does not query MediaStore or persist Photo Picker records itself.

## Metadata

| Item | Value |
|---|---|
| Type | Android application |
| Gradle path | `:app` |
| Package / namespace / application id | `com.github.sceneren.album` |
| Min / target / compile SDK | 24 / 37 / 37 |
| UI | Jetpack Compose Material3 |
| Project dependency | `:album-api` |
| Main source files | 9 |

## Responsibilities

- `MainActivity`: creates `AlbumApi`, registers permission and image/video/mixed picker launchers before start, collects UI state with lifecycle awareness, and supplies Coil image/video decoders.
- `AlbumDataClient` / `AlbumApiDataClient`: adapter that keeps the ViewModel testable while delegating data operations to the library.
- `AlbumViewModel`: owns the selected filter/directory, synchronizes PARTIAL media before refreshing access/source/directories, exposes a cached Paging flow, and reports picker outcomes.
- `MediaPermissionRequestFactory`: creates SDK- and filter-specific host permission arrays.
- `AlbumScreen`: renders access/source status, filters, directory chips, Paging load states, image/video cards, and explicit permission/picker actions.
- `ui/theme`: application Material3 theme tokens.

## Runtime Flow

1. `MainActivity` creates `AlbumApi` and registers three filter-specific picker launchers.
2. The ViewModel asks the API for access status and a feed.
3. Full access displays paged MediaStore media and directories; partial/denied access displays the persisted Photo Picker feed.
4. Permission results and `onResume` trigger refresh. Refresh first synchronizes PARTIAL media into `:album-api`; Picker results are already validated and persisted by `:album-api`, then refresh the host state.
5. `AlbumScreen` consumes `LazyPagingItems`; Coil renders content URIs and video frames.

## Dependencies

- Project: `:album-api`.
- AndroidX core, lifecycle, ViewModel Compose, Activity Compose, Paging Compose.
- Compose BOM/UI/Material3/tooling/test.
- Coil Compose, GIF, and video.
- JUnit, Robolectric, AndroidX JUnit/Espresso.

## Watch Points

- Keep permission prompts user initiated and filter-specific.
- Keep all media data/grant logic behind `AlbumApi`; the host should not duplicate it.
- Device validation is required for OEM Photo Picker, persisted grants, partial access, and real MediaStore content.
