# Dependency Reference

## Module Graph

```mermaid
graph TD
    App[":app — host UI"] --> Api[":album-api — reusable data library"]
    App --> ComposeUI[":album-ui-compose"]
    App --> ViewUI[":album-ui-view"]
    ComposeUI --> Api
    ViewUI --> Api
    ComposeUI --> VideoAPI[":album-video-api — playback contract"]
    ViewUI --> VideoAPI
    App --> Media3Engine[":album-video-media3 — optional engine"]
    Media3Engine --> VideoAPI
    Media3Engine --> Media3["Media3 ExoPlayer / UI"]
    ComposeUI --> Compose["Compose / Material3 / Paging Compose / ZoomImage Compose"]
    ViewUI --> ViewDeps["RecyclerView / ViewPager2 / ZoomImage View"]
    App --> Coil["Coil image, GIF, video"]
    Api --> Activity["AndroidX Activity Result"]
    Api --> Paging["Paging Runtime"]
    Api --> Luban["Luban Compression"]
    Api --> Coroutines["Kotlin Coroutines Android"]
```

| Module | Type | Project dependencies | Compose enabled |
|---|---|---|---|
| `:app` | Android application | `:album-api`, `:album-ui-compose`, `:album-ui-view`, `:album-video-media3` | yes |
| `:album-ui-compose` | Android library | `:album-api`, `:album-video-api` | yes |
| `:album-ui-view` | Android library | `:album-api`, `:album-video-api` | no |
| `:album-api` | Android library | none | no |
| `:album-video-api` | Android library | none | no |
| `:album-video-media3` | Optional Android library | `:album-video-api` | no |

## Important Aliases

| Scope | Aliases |
|---|---|
| Library public API | `androidx-activity-ktx`, `androidx-paging-runtime` |
| Library implementation | `androidx-core-ktx`, `androidx-lifecycle-runtime-ktx`, `kotlinx-coroutines-android`, `luban` |
| Library tests | Paging test helpers, coroutine test, Robolectric |
| UI libraries | `androidx-recyclerview`, `androidx-viewpager2`, `zoomimage-view`, `zoomimage-compose`, `androidx-paging-compose` |
| Host UI | Activity Compose, lifecycle/ViewModel Compose, Paging Compose, Compose BOM/UI/Material3 |
| Host media rendering | Coil Compose/GIF/video |
| Optional video engine | `androidx-media3-exoplayer`, `androidx-media3-ui` (1.11.1) |

## Rules

- Dependency direction is one-way: `:app` to UI/data and the optional engine; both UI libraries export `:album-api` and lightweight `:album-video-api`. Media3 must appear only under opt-in `:album-video-media3`, never transitively under either picker UI. Coil stays in the demo host.
- Add dependencies through `gradle/libs.versions.toml` and use catalog aliases.
- Keep Compose UI, Material3, Coil, permission-prompt libraries, and app-only utilities out of `:album-api`.
- Regenerate `.codex/references/_scan.json` after module, dependency, or source-layout changes.
