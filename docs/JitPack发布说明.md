# GitHub 与 JitPack 发布说明

本文面向仓库维护者，说明如何把 Album 的三个 Library 模块发布到 JitPack。JitPack 直接从 GitHub 标签或提交构建，不需要把 AAR 手工上传到 Maven 仓库。

## 发布制品

项目只发布以下模块：

| 模块 | JitPack 坐标 |
| --- | --- |
| `album-api` | `com.github.sceneren.Album:album-api:<Tag>` |
| `album-ui-view` | `com.github.sceneren.Album:album-ui-view:<Tag>` |
| `album-ui-compose` | `com.github.sceneren.Album:album-ui-compose:<Tag>` |

`app` 是演示应用，不参与 Maven 发布。JitPack 还会为多模块项目生成聚合坐标 `com.github.sceneren:Album:<Tag>`；使用者应优先依赖上表中的具体模块，以免引入不需要的 UI 实现。

## 配置说明

根目录的 `build.gradle.kts` 对所有 Android Library 应用以下规则：

- 使用 `maven-publish`。
- 只发布 `release` 变体。
- 同时生成 AAR、Gradle Module Metadata、POM 和源码 JAR。
- 项目名、发布 group/artifact/version、Android 包名和 SDK 等默认配置统一维护在 `gradle.properties`。
- 本地默认坐标为 `com.github.sceneren.Album:<模块名>:<publishedVersion>`；`publishedVersion` 同时作为 demo app 的 `versionName`。
- 在 JitPack 环境中读取 `GROUP`、`ARTIFACT` 和 `VERSION`，自动得到与仓库和标签一致的坐标；`VERSION` 也会同步覆盖 demo app 的 `versionName`。

根目录的 `jitpack.yml` 执行以下工作：

- 使用 JDK 17，满足当前 Android Gradle Plugin 的运行要求。
- 给 `gradlew` 添加 Linux 可执行权限。
- 只把三个 Library 的 `release` 发布内容安装到 JitPack 的本地 Maven 仓库。
- JitPack 构建阶段跳过测试；测试必须在创建发布标签前由维护者执行。

## 一、发布前检查

确认工作区没有遗漏的代码，并依次执行：

```powershell
.\gradlew.bat :album-api:testDebugUnitTest :app:testDebugUnitTest
.\gradlew.bat :album-api:assembleDebug :album-ui-view:assembleDebug :album-ui-compose:assembleDebug
.\gradlew.bat clean :album-api:publishReleasePublicationToMavenLocal :album-ui-view:publishReleasePublicationToMavenLocal :album-ui-compose:publishReleasePublicationToMavenLocal
```

最后一条命令成功后，本机 Maven 仓库中应存在：

```text
%USERPROFILE%\.m2\repository\com\github\sceneren\Album\
├── album-api\<publishedVersion>\
├── album-ui-view\<publishedVersion>\
└── album-ui-compose\<publishedVersion>\
```

每个目录应至少包含 `.aar`、`.pom`、`.module` 和 `-sources.jar`。重点检查两个 UI 模块的 POM/Module Metadata 是否包含对 `album-api` 的依赖。

## 二、创建 GitHub 标签

推荐使用不带 `v` 前缀的语义化版本，例如 `1.0.0`。依赖版本必须与标签完全相同；如果标签是 `v1.0.0`，使用者也必须写 `v1.0.0`。

```powershell
git status
git tag -a 0.0.2 -m "Release 0.0.2"
git push origin 0.0.2
```

示例中的 `0.0.2` 应替换为 `gradle.properties` 当前的 `publishedVersion`。标签应只指向已经通过上一步检查的提交。不要移动或覆盖已经公开使用的标签；修复后应发布新版本。

### GitHub Release 自动化

推送标签后，`.github/workflows/release.yml` 会自动执行以下步骤：

1. 校验标签与 `gradle.properties` 的 `publishedVersion` 完全一致。
2. 运行 `album-api` 和 `app` 单元测试。
3. 构建 `album-api`、`album-ui-view` 和 `album-ui-compose` 的 release AAR。
4. 创建 GitHub Release，自动生成 Release Notes，并上传三个带版本号的 AAR 和 `SHA256SUMS.txt`。

workflow 使用仓库自动提供的 `GITHUB_TOKEN`，不需要配置个人访问令牌。仓库必须允许 Actions 对 Contents 进行写入；workflow 已声明 `contents: write`。任务失败时不会创建 Release，修复后可以在 Actions 页面重新运行，已有 Release 的资产会被覆盖更新。

也可以在 GitHub 的 Releases 页面创建 Release 并填写标签。JitPack 同时支持标签、提交哈希和 `分支名-SNAPSHOT`，正式发布建议使用稳定标签。

## 三、触发并检查 JitPack 构建

1. 打开 `https://jitpack.io/#sceneren/Album`。
2. 点击 `Look up` 查询仓库。
3. 找到刚推送的标签并点击 `Get it`。
4. 等待状态变为绿色，打开构建日志确认三个发布任务都成功。

构建日志也可以直接访问：

```text
https://jitpack.io/com/github/sceneren/Album/<Tag>/build.log
```

JitPack 成功并不等于接入一定正确。发布后应在独立 Android 项目中至少验证一个 UI 模块能够解析依赖、编译并打开选择页。

## 四、验证使用者坐标

在测试项目的 `settings.gradle.kts` 添加：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

然后按需添加一个模块：

```kotlin
dependencies {
    implementation("com.github.sceneren.Album:album-ui-compose:1.0.0")
}
```

验证依赖树时，`album-ui-compose` 或 `album-ui-view` 下应自动出现 `album-api`，使用者不需要重复声明。

## 版本策略

- `1.0.0`：稳定版本，推荐用于正式发布。
- `1.0.1`：兼容性错误修复。
- `1.1.0`：向后兼容的新功能。
- `2.0.0`：包含不兼容 API 变化。
- `<commit hash>`：临时验证某次提交。
- `main-SNAPSHOT`：跟随分支最新提交，只适合开发联调。

README 中不要写尚未成功构建的版本号。新标签在 JitPack 变绿、独立项目验证通过后，再把示例中的版本更新为该稳定版本。

## 常见问题

### JitPack 提示 gradlew 无执行权限

仓库中的 Windows 工作区没有保存可执行位，`jitpack.yml` 的 `before_install` 已执行 `chmod +x gradlew`。如果修改构建命令，不要删除该步骤。

### 找不到 release publication

先运行 `./gradlew :album-api:tasks --group publishing`，确认存在 `publishReleasePublicationToMavenLocal`。Android Library 必须保留 `singleVariant("release")` 和 `maven-publish` 配置。

### JDK 或 Gradle 版本错误

当前项目使用 Android Gradle Plugin 9.3.1，要求运行 JDK 17；`jitpack.yml` 已显式选择 `openjdk17`。升级 AGP 时，需要同步核对 JitPack JDK、Gradle Wrapper 和 Android SDK 兼容性。

### 标签构建失败后仍然读取旧结果

先在 JitPack 页面查看 `build.log`。修复代码后推荐创建新标签；如确需重建失败版本，可登录 JitPack 删除失败构建后重新请求。使用者本地可加 `--refresh-dependencies` 刷新 Gradle 缓存。

### UI 模块能下载但代码中找不到 album-api 类型

确认 UI 模块使用 `api(project(":album-api"))`，而不是 `implementation`，并检查发布的 `.module` 和 `.pom` 是否包含 `album-api`。然后用新标签重新发布。

## 官方参考

- [JitPack Android Library 发布指南](https://docs.jitpack.io/android/)
- [JitPack 构建与 jitpack.yml 配置](https://docs.jitpack.io/building/)
- [Android Library 发布指南](https://developer.android.com/build/publish-library)
- [Android 发布变体配置](https://developer.android.com/build/publish-library/configure-pub-variants)
