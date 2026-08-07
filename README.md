# Album

Album 是一个 Android 相册选择器，提供数据 API、传统 View 界面和 Jetpack Compose 界面。它支持图片、视频、混合媒体、多选、系统 Photo Picker、MediaStore 分页、相机拍摄、图片压缩以及选择结果文件化。

[![](https://jitpack.io/v/sceneren/Album.svg)](https://jitpack.io/#sceneren/Album)

## 模块

| 模块 | 用途 |
| --- | --- |
| `album-api` | 媒体模型、权限判断、MediaStore/Photo Picker 数据、分页、会话、相机和结果文件处理，不包含 UI |
| `album-ui-view` | 基于 Activity、RecyclerView 和 ViewPager2 的完整选择界面 |
| `album-ui-compose` | 可嵌入宿主导航的 Jetpack Compose 选择界面 |
| `app` | 同时演示 View 和 Compose 接入方式，不作为库发布 |

最低支持 Android 7.0（API 24）。UI 模块会传递依赖 `album-api`，宿主只需声明实际使用的 UI 模块。

## 添加依赖

项目通过 JitPack 发布。先在宿主项目的 `settings.gradle.kts` 中添加仓库：

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

再在应用模块的 `build.gradle.kts` 中添加所需模块。把 `Tag` 替换为 GitHub 上已经通过 JitPack 构建的标签，例如 `1.0.0`：

```kotlin
val albumVersion = "Tag"

dependencies {
    // 传统 View 选择器
    implementation("com.github.sceneren.Album:album-ui-view:$albumVersion")

    // Compose 选择器；不使用时无需添加
    implementation("com.github.sceneren.Album:album-ui-compose:$albumVersion")

    // 仅使用数据能力时添加；UI 模块已经传递此依赖
    // implementation("com.github.sceneren.Album:album-api:$albumVersion")
}
```

多模块制品的坐标为 `com.github.sceneren.Album:<模块名>:<Tag>`。建议按需依赖单个模块，避免引入不使用的 UI 实现。

## 声明权限

宿主应用需要在 `AndroidManifest.xml` 中声明媒体读取权限：

```xml
<uses-permission
    android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED" />
```

- UI 模块会根据 `AlbumMediaFilter` 请求实际需要的权限。只使用 `album-api` 时，可用 `AlbumMediaPermissionRequestFactory.create(filter)` 生成请求列表。相机功能通过系统 Activity 完成，宿主不需要声明 `CAMERA` 权限。
- 也可以不添加权限，默认走系统 Photo Picker。打开选择界面时，可以配置不申请权限。

## 配置图片加载器

库不强制依赖 Coil 或 Glide。宿主必须在自定义 `Application.onCreate()` 中为所使用的 UI 模块配置 `AlbumImageLoader`，并在 Manifest 的 `<application android:name="...">` 中注册该 Application。

下面是 View 使用 Coil 的最小示例：

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        com.github.sceneren.album.ui.view.AlbumUi.setImageLoader(
            object : com.github.sceneren.album.ui.view.AlbumImageLoader {
                override fun load(
                    imageView: ImageView,
                    media: AlbumMedia,
                    target: com.github.sceneren.album.ui.view.AlbumImageTarget,
                ) {
                    imageView.load(media.uri)
                }

                override fun clear(imageView: ImageView) {
                    imageView.load(null)
                }
            },
        )
    }
}
```

Compose 的 Coil 配置、视频封面解码和完整导入见[相册选择器使用说明](docs/相册选择器使用说明.md)。

## View 快速开始

```kotlin
private val albumPicker = registerForActivityResult(
    com.github.sceneren.album.ui.view.AlbumPickerContract(),
) { result ->
    result?.items.orEmpty().forEach { item ->
        // 应用内处理文件时使用 item.filePath
        // 需要 URI 或跨组件传递时使用 item.resultUri
    }
}

private fun openAlbum() {
    albumPicker.launch(
        com.github.sceneren.album.ui.view.AlbumPickerRequest(
            config = AlbumPickerConfig(
                mediaFilter = AlbumMediaFilter.IMAGES_AND_VIDEOS,
                maxSelectionCount = 9,
            ),
        ),
    )
}
```

## Compose 快速开始

```kotlin
var showAlbum by rememberSaveable { mutableStateOf(false) }

if (showAlbum) {
    com.github.sceneren.album.ui.compose.AlbumPicker(
        config = AlbumPickerConfig(
            mediaFilter = AlbumMediaFilter.IMAGES,
            maxSelectionCount = 9,
        ),
        onResult = { result ->
            handleAlbumResult(result)
            showAlbum = false
        },
        onCancel = { showAlbum = false },
    )
} else {
    Button(onClick = { showAlbum = true }) {
        Text("选择图片")
    }
}
```

Compose 版本不声明独立 Activity。宿主负责把 `AlbumPicker` 放入自己的导航或条件布局，并在完成或取消后将其移除。

## 结果与文件

确认选择后，媒体会物化到应用专属外部存储目录；相同 URI、媒体版本和大小会复用已存在的完整文件。`AlbumPickerResultItem` 的主要字段：

- `originalUri`：系统返回的原始媒体 URI。
- `originalFilePath`：复制到 `files/photo_picker` 后的原始文件绝对路径。
- `filePath`：最终文件绝对路径；启用并完成图片压缩后指向压缩文件。
- `resultUri`：最终文件对应的 URI，需要 URI 形式时使用。
- `compressionStatus`：未启用、按大小跳过、已压缩或视频不适用。

生成文件位于应用专属目录，卸载应用时会被系统清理。应用也可以通过 `AlbumApi.deleteGeneratedMedia()` 或 `AlbumApi.clearGeneratedMedia()` 主动清理。相同媒体的多个结果可能持有同一路径，因此 `deleteGeneratedMedia()` 表示驱逐共享缓存；调用前应确保该路径不再被使用。

## 更多文档

- [完整接入与使用说明](docs/相册选择器使用说明.md)
- [GitHub 与 JitPack 发布说明](docs/JitPack发布说明.md)
- [JitPack Android Library 官方指南](https://docs.jitpack.io/android/)
- [Android Library 发布官方指南](https://developer.android.com/build/publish-library)

## 本地验证

```powershell
.\gradlew.bat :album-api:testDebugUnitTest :app:testDebugUnitTest
.\gradlew.bat :album-api:assembleDebug :album-ui-view:assembleDebug :album-ui-compose:assembleDebug
```

发布版本前还应运行本地 Maven 发布验证，具体命令见[GitHub 与 JitPack 发布说明](docs/JitPack发布说明.md)。
