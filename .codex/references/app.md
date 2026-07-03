# Module: app

## Overview

`app` is the only Android application module in Album. It implements a Jetpack Compose image gallery that requests image permissions, reads device images through MediaStore, groups images by directory, displays them in a lazy grid, supports load-more pagination, and can open the Android Photo Picker through the Activity Result API with a Google Play services module fallback.

## Metadata

| Item | Value |
|---|---|
| Type | Android application |
| Gradle path | `:app` |
| Source root | `app/src/main/java` |
| Package | `com.github.sceneren.album` |
| Namespace | `com.github.sceneren.album` |
| Application id | `com.github.sceneren.album` |
| Min SDK | 24 |
| Target SDK | 37 |
| Compile SDK | 37 |
| UI | Jetpack Compose Material3 |
| Source files | 15 |
| Tests | Template unit test and instrumented package-name test |

## Directory Structure

```text
app/
  build.gradle.kts
  src/main/AndroidManifest.xml
  src/main/java/com/github/sceneren/album/
    App.kt
    MainActivity.kt
    AlbumViewModel.kt
    AlbumLoader.kt
    FileHelper.kt
    ImageDirectory.kt
    ImageItem.kt
    PagedResult.kt
    refresh/
      Footer.kt
      LoadMoreState.kt
      RefreshLazyColumn.kt
      RefreshLazyVerticalGrid.kt
    ui/theme/
      Color.kt
      Theme.kt
      Type.kt
  src/main/res/
    values/
    xml/
    drawable/
    mipmap-*/
```

## Key Classes and APIs

### `MainActivity`

- Extends `ComponentActivity`.
- Calls `enableEdgeToEdge()`, initializes `AlbumLoader` and `FileHelper`, configures Coil GIF/WebP-capable image loading, and sets Compose content.
- Collects `AlbumViewModel` flows with `collectAsStateWithLifecycle`.
- Delegates media loading to `AlbumViewModel`.

### `TestAlbum`

- Main composable for the current gallery screen.
- Handles XXPermissions image permission request.
- Registers `ActivityResultContracts.PickVisualMedia`.
- Checks and installs the backported Photo Picker module through Google Play services.
- Shows directory selection, loaded image count, first image highlight, image grid, GIF/WebP badges, and file-cache path lookup on item click.

### `AlbumViewModel`

- Owns `StateFlow` state for directories, images, selected directory, load-more state, and whether more pages exist.
- `getImageDirectories()` loads directories and selects the virtual all-images directory.
- `setCurrentDir(directory)` resets page state and loads the first page.
- `loadMoreImages()` increments page and appends returned images.

### `AlbumLoader`

- Singleton MediaStore query utility.
- Must be initialized with `init(context)` before use.
- Uses `applicationContext` and `Dispatchers.IO`.
- Public APIs:
  - `getImageDirectories(): List<ImageDirectory>`
  - `getAllImages(page, pageSize): PagedResult<ImageItem>`
  - `getImagesByDirectory(bucketId, page, pageSize): PagedResult<ImageItem>`
- Uses Bundle query arguments on API 30+ and `LIMIT/OFFSET` sort-order paging on API 24-29.

### `FileHelper`

- Singleton content-URI file helper.
- Must be initialized with `init(context)` before use.
- Copies a `content://` URI into app cache under `album_cache`.
- Public APIs:
  - `getFileUrl(uri): String?`
  - `getFileUrl(uris): Map<Uri, String>`
  - `clearCache(): Int`
  - `getCacheSize(): Long`

### Models

- `ImageItem`: immutable image metadata from MediaStore, including id, uri, display name, size, dates, MIME type, dimensions, bucket id, and bucket name. Computed flags: `isGif`, `isWebp`.
- `ImageDirectory`: immutable album/directory summary. `ALL_BUCKET_ID` represents the virtual all-images directory.
- `PagedResult<T>`: page data and pagination metadata.

### Refresh Package

- `LoadMoreState`: `IDLE`, `LOADING`, `ERROR`.
- `Footer`: renders loading, error retry, or no-more-data footer.
- `RefreshLazyColumn`: pull-to-refresh plus auto-load-more for lazy columns.
- `RefreshLazyVerticalGrid`: pull-to-refresh plus auto-load-more for lazy grids.

### Theme Package

- `AlbumTheme`: Material3 theme with dynamic color on Android 12+.
- `Color.kt` and `Type.kt`: starter Material3 color and typography tokens.

## External Dependencies

- AndroidX core KTX, lifecycle runtime KTX, lifecycle ViewModel Compose.
- AndroidX Activity Compose.
- Compose BOM, Compose UI, tooling, Material3, UI tests.
- Coil 3 compose, network okhttp, GIF support.
- XXPermissions.
- DeviceCompat.
- Google Play services base.
- JUnit, AndroidX JUnit, Espresso.

## Runtime Flow

1. `MainActivity.onCreate` initializes helpers and Compose.
2. `TestAlbum` checks image permission in a `LaunchedEffect(Unit)`.
3. On permission success, `AlbumViewModel.getImageDirectories()` calls `AlbumLoader.getImageDirectories()`.
4. The virtual all-images directory is selected.
5. `AlbumViewModel` loads page 1 through `AlbumLoader.getImagesByDirectory()`.
6. `RefreshLazyVerticalGrid` triggers `loadMoreImages()` near the bottom while `hasMoreData` is true and state is `IDLE`.
7. `AsyncImage` renders content URIs through Coil.
8. Clicking an image calls `FileHelper.getFileUrl(uri)` to copy it into app cache and expose a local path.

## Risks and Watch Points

- Permission flows must be tested on API 24-32 and API 33+ because storage/media permissions differ.
- The screen currently contains demo hardcoded UI text and colors; avoid adding more product text/colors outside resources or theme tokens.
- `AlbumViewModel.loadMoreImages()` increments `currentPage` before the query returns; error handling should be added before introducing retry behavior.
- MediaStore and Photo Picker behavior require device or emulator validation with real image content.

