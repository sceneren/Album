---
name: code_review
description: Review Album project changes for Android/Kotlin/Compose correctness, MediaStore safety, and project-rule compliance.
---

# Album Code Review

Use a findings-first review. Prioritize bugs, regressions, missing tests, and Android policy/runtime risks.

## Fatal Checks

- MediaStore query, file copy, cache cleanup, or large image processing on the main thread.
- Storage permission changes that break API 24-37 compatibility or remove required SDK gates.
- New dependency, module, class, or resource reference that is not declared in Gradle or present in the repo.
- `AlbumLoader` or `FileHelper` API use before initialization.
- Pagination logic that can skip pages, duplicate pages, or load indefinitely after `hasNextPage` is false.
- Compose state that causes repeated permission prompts or repeated load-more calls after recomposition.

## Warning Checks

- Hardcoded product strings or colors added to UI instead of using resources/theme tokens.
- `Log.e` or debug-only messages left in normal successful paths.
- New public API without KDoc when it is part of reusable media/loading logic.
- Reusable list/grid behavior changed in only one of `RefreshLazyColumn` or `RefreshLazyVerticalGrid`.
- File cache paths exposed without cleanup or size considerations.

## Suggested Checks

- Add unit tests around pure pagination calculations or helper behavior when feasible.
- Add instrumented/device verification notes for permission, MediaStore, or Photo Picker behavior.
- Prefer small composables and focused helpers when `MainActivity.kt` grows further.

## Output Format

List findings first with file and line references. If no issues are found, say so clearly and mention any untested Android-version or device-media risk.

