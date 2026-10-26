# Module: album-ui-compose

Reusable Compose UI library depending on `:album-api`, Compose, Paging Compose and base ZoomImage; it does not depend on Coil or host navigation.

- `AlbumPicker`: complete page with title, directory control, grid, preview, confirmation, and optional enter/exit transitions.
- `AlbumPickerSystemBarStyle`: optional colors, explicit/automatic icon brightness and navigation contrast enforcement. Passing null to `AlbumPicker.systemBarStyle` leaves Window styling to the host. Default handling restores original Window state on disposal.
- `AlbumPickerContent`: embedded directory/grid/camera/add/preview region. No host title/confirmation bar, system-bar mutations/insets, or page transitions. Host bounds its size and provides actions.
- `rememberAlbumPickerState` / `AlbumPickerState`: selected media, processing status, confirmation availability and host confirm/preview/cancel/refresh actions. One state binds to one active content component; callbacks detach on disposal.
- Shared `AlbumPickerHost` retains permissions, picker/camera launchers, persisted sessions and result generation for both variants. Embedded back handling is restricted to internal directory/preview overlays.
- `AlbumImageLoader` / `AlbumUi`: host-supplied Painter integration for thumbnails and previews.

Resources use `auc_`. Both UI variants surface Paging errors with retry; neither changes API-layer permission routing. Tests cover presentation, state ownership/actions and system-bar apply/restore. Device checks are required for embedding geometry, system insets and Activity Result flows.
