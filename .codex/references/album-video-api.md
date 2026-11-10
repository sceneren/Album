# Module: album-video-api

A lightweight Android Library containing only `AlbumVideoPlayerFactory`, `AlbumVideoPlayer` and `AlbumVideoPlaybackState` in `com.github.sceneren.album.video`. It depends on neither the media data API, picker UI, Media3, Compose nor Coil.

The shared interface provides a parentless aspect-fit View, asynchronous content-URI load with position/play/mute, cheap playback snapshots, main-thread change callbacks, play/mute/seek/retry and idempotent release. The picker UI owns rendering attachment, controls and foreground lifecycle; custom backends handle their own IO, decoding and audio interruptions. A missing UI factory must preserve static VIDEO_COVER rather than throwing or loading a default backend.

Both picker UI libraries export this module with `api`; engines are independently optional. Factory instances must not hold Activity context across release. Public KDoc defines the contract; custom fake implementations in UI tests validate backend independence.
