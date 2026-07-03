# Architecture Review Agent

Use this checklist when a change affects module boundaries, data flow, permissions, MediaStore access, or reusable Compose components.

## Module Structure

- [ ] Project still builds as root `Album` with module `:app`.
- [ ] No undeclared Gradle modules or aliases were referenced.
- [ ] New dependencies are declared in `gradle/libs.versions.toml` and used through version catalog aliases.

## Layering

- [ ] `MainActivity` and composables handle UI, launchers, and user events only.
- [ ] `AlbumViewModel` owns media list state, selected directory state, page number, load-more state, and `hasMore`.
- [ ] `AlbumLoader` owns MediaStore queries and returns `ImageDirectory`, `ImageItem`, or `PagedResult`.
- [ ] `FileHelper` owns `content://` to cache-file conversion and cleanup.

## Android Media

- [ ] Permissions remain compatible with API 24-37.
- [ ] Media queries and file operations stay off the main thread.
- [ ] `content://` URI behavior is preserved.
- [ ] Photo Picker module-install fallback is preserved.

## Compose

- [ ] Flow state is collected with lifecycle awareness.
- [ ] Side effects use stable keys and do not retrigger permission or load-more flows unexpectedly.
- [ ] Shared refresh behavior remains consistent between list and grid components.

