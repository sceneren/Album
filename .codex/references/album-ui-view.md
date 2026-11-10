# Module: album-ui-view

Reusable Android View UI library depending on `:album-api`, `:album-video-api`, RecyclerView/ViewPager2 and base ZoomImageView. No Compose, Coil or Media3 dependency.

`AlbumPickerActivity` owns the full-screen picker lifecycle, permission/camera launchers, Paging collection, selection and preview controls. Same-filter/directory/access refreshes call `GalleryAdapter.refresh()` rather than cancelling the Pager, retaining the visible snapshot and anchor while new media loads; route changes still replace the feed. `GalleryAdapter` renders paged media through a host-supplied `AlbumImageLoader`. Image previews route through `AlbumImageLoader.loadPreview` (default PREVIEW_IMAGE) with FIT_CENTER. `PreviewSharedElementTransition` captures a currently bound visible thumbnail Drawable and screen bounds, then expands it to fitted preview bounds in the Dialog overlay without bitmap copies; cancellation/dismissal restores pager and chrome and removes the overlay. Resources use `auv_`. Refresh/append errors show a retry button; failed system-picker processing displays selection failure before refresh.

`AlbumUi.setVideoPlayerFactory` optionally supplies an engine for full-screen video with a floating existing back icon and a 48dp play/seek/mute capsule. Without a factory, previews retain static VIDEO_COVER and original chrome. `internal/video` owns one current attached/settled/RESUMED player, releases on drag/background/dismissal, and restores per-URI position/pause plus preview-wide mute. Fake-engine tests cover lifecycle, controls, fallback, neighboring pages, insets and geometry.

Configuration, file materialization, persistence and result contracts are shared through `album-api`. Runtime permission and real-provider validation requires a device.

Foreground resume and batched provider notifications enter a conflated, serialized change check. Unchanged data never refreshes the Pager; real changes update the retained adapter using URI/content diffs. The API retains the loaded window by surviving URI so RecyclerView can preserve its anchor and pixel offset. Provider observation is RESUMED-scoped and cancels its registrations when the picker stops.

Every foreground resume queues a change check, even if an asynchronous system-picker result arrived after the preceding resume. There is no cross-resume suppression flag. `PickerResumeCheckTest` dispatches a real Activity Result after resume and verifies the next foreground transition still checks deletions; unchanged data continues to avoid Pager refresh.
