---
name: plan_mode
description: Plan Android/Kotlin/Compose work for the Album project using local rules and references.
---

# Album Plan Mode

Use this skill for multi-file work, architecture changes, permission/media changes, or tasks where the implementation path is not obvious.

## Required Context

1. Read `.codex/rules/project_rule.md`.
2. Read `.codex/references/_scan.json`.
3. Read the relevant module reference, usually `.codex/references/app.md`.
4. Inspect the target source files before proposing or editing code.

## Common Task Templates

### Add or Change Compose UI

1. Identify whether the UI belongs in `MainActivity.kt` or a new composable file under the same package.
2. Keep source of truth in `AlbumViewModel` when state survives recomposition or participates in loading.
3. Use `collectAsStateWithLifecycle` for flows exposed to UI.
4. Use `MaterialTheme.colorScheme` and theme tokens for product styling.
5. Add previews only when dependencies make them compile without device media access.

### Change MediaStore Loading

1. Update `AlbumLoader` only after checking permissions and paging behavior.
2. Keep queries on `Dispatchers.IO`.
3. Preserve `content://` URI outputs.
4. Support both API 30+ Bundle query arguments and API 24-29 sort-order paging unless intentionally changing min SDK.
5. Add tests or focused verification for page boundaries and empty-result handling when feasible.

### Change Permission or Photo Picker Flow

1. Confirm Manifest permissions and SDK gates.
2. Keep XXPermissions usage consistent unless migrating the whole flow.
3. Preserve Google Photo Picker module install fallback.
4. Avoid adding unbounded storage permissions.

### Change Pagination or Refresh Components

1. Check `LoadMoreState`, `Footer`, `RefreshLazyColumn`, and `RefreshLazyVerticalGrid`.
2. Keep auto-load triggers guarded by `hasMoreData`, `LoadMoreState.IDLE`, and refresh state.
3. Avoid repeated load triggers caused by recomposition.
4. Verify both list and grid variants when shared behavior changes.

## Plan Output

Plans should state files to touch, expected behavior change, verification command, and any risk around permissions, device media, or Android version behavior.

