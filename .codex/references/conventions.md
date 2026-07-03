# Album Coding Conventions

## Kotlin and Compose

- Use Kotlin official style with 4-space indentation.
- Keep Compose UI declarative and side-effect-light.
- Put long-lived state in `AlbumViewModel`; use Compose local state only for short UI state such as dropdown expansion.
- Collect flows with lifecycle-aware APIs in UI.
- Prefer `MaterialTheme.colorScheme` and centralized theme tokens over hardcoded colors.
- Product strings should move to `strings.xml` when they are no longer temporary/demo text.

## MediaStore and Storage

- Use `content://` URIs as the primary representation of media.
- Use `ContentResolver` and MediaStore projections instead of deprecated raw file paths.
- Use `Dispatchers.IO` for queries and file operations.
- Close cursors with `use`.
- Keep API 30+ Bundle query logic and API 24-29 fallback logic aligned.

## Permissions

- Keep Manifest SDK gates for legacy storage permissions.
- Use Android 13+ `READ_MEDIA_IMAGES` and related media permissions for modern devices.
- Request runtime permissions through the existing XXPermissions flow unless a migration is planned.
- Do not repeatedly prompt from recomposition.

## Naming

- Activity: `{Feature}Activity`.
- ViewModel: `{Feature}ViewModel`.
- Data models: descriptive nouns such as `ImageItem`.
- Compose functions: PascalCase and UI-oriented names.
- Resource names: lowercase snake_case.

## Tests

- Unit tests: `app/src/test/java`.
- Instrumented tests: `app/src/androidTest/java`.
- Add focused tests for pagination and helper logic where possible.
- Device/emulator checks are required for MediaStore, permissions, and Photo Picker behavior.

## Build Commands

- Debug build: `./gradlew.bat :app:assembleDebug`.
- Unit tests: `./gradlew.bat :app:testDebugUnitTest`.
- Instrumented tests: `./gradlew.bat :app:connectedDebugAndroidTest`.

