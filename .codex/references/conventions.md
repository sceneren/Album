# Album Coding Conventions

## Kotlin and Module Boundaries

- Use Kotlin official style with 4-space indentation and immutable public models.
- Public reusable library APIs require concise KDoc; implementation details stay under `.internal`.
- Keep `:album-api` data-only and Compose-free. Compose functions, UI state presentation, theme tokens, and Coil belong to `:app`.
- Collect host flows with lifecycle-aware APIs and keep long-lived state in `AlbumViewModel`.

## Media and Persistence

- Use `content://` URIs and `ContentResolver`; never depend on raw external paths.
- Run MediaStore, Room, metadata, and grant operations on an IO dispatcher.
- Close cursors with `use` and keep projections minimal, especially for full-library directory aggregation.
- Preserve deterministic order for paging and persisted selections.
- Treat a Photo Picker batch atomically at the data layer; validate before committing, and clean up newly acquired grants after a failure.
- Chunk collection-bound SQL queries so the default-unlimited library API remains compatible with older SQLite bind limits.

## Permissions

- The host declares and requests runtime media permissions; the library only resolves current access.
- Apply the active image/video/mixed filter to both permission resolution and data selection.
- Full access uses MediaStore; partial and denied access use persisted Photo Picker selections.
- Do not repeatedly prompt from composition or automatically prompt on every resume.

## UI

- Use `MaterialTheme.colorScheme`, resources, adaptive lazy grids, and explicit Paging loading/error/empty states.
- Decode media through Coil; do not manually decode bitmaps in list cells.
- Product text belongs in `strings.xml`; locale-independent technical formatting uses `Locale.ROOT`.

## Tests and Commands

- Library tests: `album-api/src/test/java`; host tests: `app/src/test/java` and `app/src/androidTest/java`.
- Test permission policy, picker contracts/results, grant ownership/rollback, Room ordering/filtering, and Paging offsets.
- Unit tests: `./gradlew.bat :album-api:testDebugUnitTest :app:testDebugUnitTest`.
- Build/lint: `./gradlew.bat :album-api:assembleDebug :app:assembleDebug :album-api:lintDebug :app:lintDebug`.
- Real MediaStore, permission, and Photo Picker behavior requires an Android device or emulator.
