# Module: album-ui-compose

Reusable Compose UI library depending on `:album-api`, `:album-video-api`, Compose, Paging Compose and base ZoomImage; it does not depend on Coil, Media3 or host navigation.

- `AlbumPicker`: complete page with title, directory control, grid, preview, confirmation, and optional enter/exit transitions.
- `AlbumPickerSystemBarStyle`: optional colors, explicit/automatic icon brightness and navigation contrast enforcement. Passing null to `AlbumPicker.systemBarStyle` leaves Window styling to the host. Default handling restores original Window state on disposal.
- `AlbumPickerContent`: embedded directory/grid/camera/add/preview region. No host title/confirmation bar, system-bar mutations/insets, or page transitions. Host bounds its size and provides actions.
- `rememberAlbumPickerState` / `AlbumPickerState`: selected media, processing status, confirmation availability and host confirm/preview/cancel/refresh actions. One state binds to one active content component; callbacks detach on disposal.
- Shared `AlbumPickerHost` retains permissions, picker/camera launchers, persisted sessions and result generation for both variants. Same-filter/directory/access refreshes reuse the current Pager/presenter and call `refresh()`; route changes still create a new feed. The grid stays composed during refresh, covers only empty initial loading, and retains scroll state for the current route. Embedded back handling is restricted to internal directory/preview overlays.
- `AlbumImageLoader` / `AlbumUi`: host-supplied Painter integration; `previewPainter` defaults to PREVIEW_IMAGE and documents original-source, bounded decoding with Coil thumbnail-cache placeholders.
- `AlbumPreviewImage` owns the common SharedTransitionLayout, URI-matched grid/preview image transitions and fitted transition bounds. The grid stays composed; AnimatedContent retains the outgoing preview for return animation. Full-viewport ZoomImage takes over once the shared element settles; video playback is suspended while exiting or transitioning.
- Optional `AlbumUi.setVideoPlayerFactory` or per-call `videoPlayerFactory` on both pickers supplies engine-independent video playback. Explicit null means static VIDEO_COVER with original chrome; no Media3 comes from this module.
- `AlbumVideoPreview` / `internal/video/VideoPreviewSession` render the engine View through AndroidView and own the floating back/capsule play/seek/mute controls. Only the settled foreground video creates a player; drag/background/disposal release it, preserving per-URI position/pause and preview-wide mute. Robolectric Compose tests exercise custom engines, fallback, controls and prefetched pages.

Resources use `auc_`. Both UI variants surface Paging errors with retry; neither changes API-layer permission routing. Tests cover presentation, state ownership/actions and system-bar apply/restore. Device checks are required for embedding geometry, system insets and Activity Result flows.
