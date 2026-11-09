# Module: app

`:app` is the demonstration host for the three reusable Album libraries. Its package/namespace is `com.github.sceneren.photopicker` (see `gradle.properties`), with min SDK 24 and compile/target SDK 37.

- `MainActivity` demonstrates View full-page selection, Compose full-page selection, and `AlbumPickerContent` embedded between host title/actions. It consumes results without duplicating media data handling.
- `App` configures Coil-backed loaders for both UI modules and video decoding. Grid requests use `SystemThumbnailFetcher` (`ContentResolver.loadThumbnail`, API 29+) first, with size-bounded original fallback; Compose uses bounded device-derived request sizes so painter restarts do not wait for a draw-size resolver. Preview requests are separate from grid thumbnail requests.
- `PhotoPickerImageFetcher` handles authorized Photo Picker image content streams; Coil remains in the host, outside both UI libraries and `album-api`.
- Thumbnail requests have a versioned URI/metadata cache key separate from previews, use FIT/inexact cache validation for aspect-preserving system thumbnails, and seed recycled Compose cells synchronously from Coil's bounded memory cache to avoid an empty first frame on scroll-back. Cache eviction/first visits can still require asynchronous loading.
- `ui/theme` owns host Material3 styling.

Project dependencies: `:album-api`, `:album-ui-view`, `:album-ui-compose`. Verify with `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:lintDebug`. Real provider compatibility, system bars and media/permission flows still require device validation.
