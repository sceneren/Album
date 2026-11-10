# Module: album-video-media3

Optional Android Library implementing `album-video-api` with Media3 1.11.1. It depends only on that contract and Media3 ExoPlayer/UI, not on `album-api`, either picker UI, Compose or Coil. Namespace: `com.github.sceneren.album.video.media3`; resources use `amv_`.

`Media3AlbumVideoPlayerFactory()` creates fresh, application-context instances. `internal/Media3AlbumVideoPlayer` exposes a TextureView-based aspect-fit PlayerView with no transport UI, handles asynchronous content URI playback, event snapshots, first frame, error/retry, seek and idempotent release without removing the picker-owned parent View. There is no media copy, provider query, additional permission, or progress timer in the adapter. The UI controls play/mute intent and lifecycle; audio focus is requested only for unmuted playback, with audio-becoming-noisy handling.

The demo explicitly depends on and registers this factory for both UI modules; installing it alone does not auto-register or force playback. Custom/static-cover hosts can omit it. Robolectric tests cover factory independence, rendering configuration, state, callbacks, audio/mute, seek, retry and surface/decoder cleanup. Real decoder/provider/audio routing still requires device checks.
