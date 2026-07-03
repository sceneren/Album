# Resource Sync Agent

Use this checklist when changing Manifest entries, resources, launcher assets, text, colors, or theme.

## Manifest and Permissions

- [ ] Package-facing behavior still uses `com.github.sceneren.album`.
- [ ] `MainActivity` remains exported for launcher intent if it is still the app entry point.
- [ ] Storage/media permissions keep current API caps and Android 13+ media permission coverage.
- [ ] Google Photo Picker service metadata remains consistent with `play-services-base`.

## Resources

- [ ] Product-facing strings are in `app/src/main/res/values/strings.xml` when practical.
- [ ] Theme-level colors are in Compose theme files or `colors.xml`; avoid spreading hardcoded feature colors.
- [ ] Launcher icons remain present across density directories.
- [ ] Backup and data extraction XML files remain referenced correctly from the Manifest.

## Compose Theme

- [ ] New UI uses `AlbumTheme` and Material3 tokens.
- [ ] Dynamic color behavior on Android 12+ is not broken unless explicitly changed.
- [ ] Typography changes stay centralized in `ui/theme/Type.kt`.

