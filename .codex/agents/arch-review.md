# Architecture Review Agent

Use this checklist when a change affects module boundaries, data flow, permissions, MediaStore access, or reusable Compose components.

## Module Structure

- [ ] Project still builds as root `Album` with module `:app`.
- [ ] No undeclared Gradle modules or aliases were referenced.
- [ ] New dependencies are declared in `gradle/libs.versions.toml` and used through version catalog aliases.

## Layering

- [ ] `MainActivity` and demo composables in `:app` handle demo configuration, launchers, and result display only.
- [ ] `:album-ui-view` (`AlbumPickerActivity`) and `:album-ui-compose` (`AlbumPicker` / `AlbumPickerContent`) own picker UI state and delegate data/session work to `:album-api`.
- [ ] `AlbumApi` and `AndroidMediaStoreDataSource` own MediaStore and system-authorized URI queries and return `AlbumDirectory`, `AlbumMedia`, or `AlbumMediaFeed`.
- [ ] `AlbumPickerClient` and `AlbumFileMaterializer` own `content://` to app-specific file materialization, compression, and cleanup.

## Android Media

- [ ] Permissions remain compatible with API 24-37.
- [ ] Media queries and file operations stay off the main thread.
- [ ] `content://` URI behavior is preserved.
- [ ] Photo Picker module-install fallback is preserved.

## Compose

- [ ] Flow state is collected with lifecycle awareness.
- [ ] Side effects use stable keys and do not retrigger permission or load-more flows unexpectedly.
- [ ] Shared refresh behavior remains consistent between list and grid components.

