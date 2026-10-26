# Module: app

`:app` is the demonstration host for the three reusable Album libraries. Its package/namespace is `com.github.sceneren.photopicker` (see `gradle.properties`), with min SDK 24 and compile/target SDK 37.

- `MainActivity` demonstrates View full-page selection, Compose full-page selection, and `AlbumPickerContent` embedded between host title/actions. It consumes results without duplicating media data handling.
- `App` configures Coil-backed loaders for both UI modules, preserving grid-size resolution and video decoding.
- `PhotoPickerImageFetcher` handles authorized Photo Picker image content streams; Coil remains in the host, outside both UI libraries and `album-api`.
- `ui/theme` owns host Material3 styling.

Project dependencies: `:album-api`, `:album-ui-view`, `:album-ui-compose`. Verify with `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:lintDebug`. Real provider compatibility, system bars and media/permission flows still require device validation.
