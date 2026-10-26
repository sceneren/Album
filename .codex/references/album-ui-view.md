# Module: album-ui-view

Reusable Android View UI library depending on `:album-api`, RecyclerView/ViewPager2 and base ZoomImageView. No Compose or Coil dependency.

`AlbumPickerActivity` owns the full-screen picker lifecycle, permission/camera launchers, Paging collection, selection and preview controls. `GalleryAdapter` renders paged media through a host-supplied `AlbumImageLoader`. Resources use `auv_`. Refresh/append errors show a retry button; failed system-picker processing displays selection failure before refresh.

Configuration, file materialization, persistence and result contracts are shared through `album-api`. Runtime permission and real-provider validation requires a device.
