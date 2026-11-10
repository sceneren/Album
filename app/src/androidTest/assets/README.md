# Video playback fixture

`media3_sample.mp4` is the 101,674-byte MP4 regression fixture from AndroidX Media 1.11.1:

https://raw.githubusercontent.com/androidx/media/1.11.1/libraries/test_data/src/test/assets/media/mp4/sample.mp4

Upstream project: https://github.com/androidx/media (Apache License 2.0).

It is packaged only in the instrumented test APK. `VideoPlaybackIntegrationTest` copies it to the app-owned FileProvider directory and uses a content URI so device playback does not need access to the user's library or network.
