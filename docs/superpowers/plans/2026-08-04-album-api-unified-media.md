# Album API Unified Visual Media Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a reusable **:album-api** Android Library that exposes filter-aware image/video paging from MediaStore when access is FULL and persisted Photo Picker paging when access is PARTIAL or DENIED, while **:app** remains a Compose host demo.

**Architecture:** A public AlbumApi facade owns filter-aware permission resolution and routes each immutable feed request to either a MediaStore PagingSource or a Room PagingSource. Photo Picker launchers are registered without Compose, validate count and media type, retain URI read grants, and commit each selection batch transactionally. The app injects a small AlbumDataClient adapter into its ViewModel, requests permissions itself, and renders PagingData.

**Tech Stack:** Android Gradle Plugin 9.3.1 with built-in Kotlin, Kotlin 2.4.10, AndroidX Activity 1.13.0, Paging 3.5.0, Room 2.8.4, KSP 2.3.10, kotlinx.coroutines 1.11.0, Robolectric 4.16.1, Jetpack Compose Material3, Coil 3.5.0.

## Global Constraints

- Module boundary: reusable Android Library **:album-api**; **:app** is only a host demo.
- Namespace: **com.github.sceneren.album.api**; min SDK 24; compile and target SDK 37.
- **:album-api** must not depend on Compose, Paging Compose, Material3, Coil, XXPermissions, DeviceCompat, or custom UI.
- Public media filters are IMAGES, VIDEOS, and IMAGES_AND_VIDEOS; the default is IMAGES.
- The same filter must control permission status, MediaStore paging, Room paging, directory aggregation, and Photo Picker input.
- Only FULL routes to MediaStore; PARTIAL and DENIED route to persisted Photo Picker records.
- PARTIAL MediaStore rows must not be imported into or mixed with the Room feed.
- maxSelectionCount is configurable; null means no library-level cap, 1 uses PickVisualMedia, values greater than 1 use PickMultipleVisualMedia, and non-positive values fail immediately.
- Every successful picker batch retains read access across process restarts; batch validation and Room visibility are all-or-nothing.
- MediaStore queries run on Dispatchers.IO, return content URIs, sort by DATE_ADDED DESC then _ID DESC, use Bundle limit/offset on API 30+, and use SQL LIMIT/OFFSET on API 24-29.
- The library Manifest declares no storage/media permission; the host declares and requests permissions.
- Preserve the user’s existing uncommitted version-catalog upgrades. Do not stage their changed AGP, Kotlin, AndroidX, Compose, or Coil version lines as part of implementation commits.
- Never stage **.codex/scripts/__pycache__/**.
- Use apply_patch for every source/config/document edit.

---

## File Structure

### Root and dependency files

- Modify **settings.gradle.kts**: include **:album-api**.
- Modify **build.gradle.kts**: declare Android Library and KSP plugin aliases with apply false.
- Modify **gradle/libs.versions.toml**: add only the new dependency/plugin keys; preserve all existing user-edited values.

### album-api public API

- Create **album-api/build.gradle.kts**: Android Library, Room/KSP, Paging, non-Compose dependencies, Robolectric tests.
- Create **album-api/consumer-rules.pro**.
- Create **album-api/src/main/AndroidManifest.xml**: Photo Picker backport metadata only.
- Create **album-api/src/main/java/com/github/sceneren/album/api/AlbumMediaFilter.kt**.
- Create **album-api/src/main/java/com/github/sceneren/album/api/AlbumMedia.kt**.
- Create **album-api/src/main/java/com/github/sceneren/album/api/AlbumDirectory.kt**.
- Create **album-api/src/main/java/com/github/sceneren/album/api/AlbumMediaFeed.kt**.
- Create **album-api/src/main/java/com/github/sceneren/album/api/AlbumPhotoPickerLauncher.kt**.
- Create **album-api/src/main/java/com/github/sceneren/album/api/AlbumApi.kt**.

### album-api permission and routing

- Create **album-api/src/main/java/com/github/sceneren/album/api/internal/permission/MediaPermissionSnapshot.kt**.
- Create **album-api/src/main/java/com/github/sceneren/album/api/internal/permission/MediaAccessPolicy.kt**.
- Create **album-api/src/main/java/com/github/sceneren/album/api/internal/permission/AndroidMediaAccessResolver.kt**.

### album-api Room persistence

- Create **album-api/src/main/java/com/github/sceneren/album/api/internal/database/PickedMediaEntity.kt**.
- Create **album-api/src/main/java/com/github/sceneren/album/api/internal/database/PickedMediaDao.kt**.
- Create **album-api/src/main/java/com/github/sceneren/album/api/internal/database/AlbumDatabase.kt**.
- Create **album-api/src/main/java/com/github/sceneren/album/api/internal/database/PickedMediaStore.kt**.
- Create **album-api/src/main/java/com/github/sceneren/album/api/internal/database/RoomPickedMediaStore.kt**.
- Generate and commit **album-api/schemas/com.github.sceneren.album.api.internal.database.AlbumDatabase/1.json**.

### album-api MediaStore

- Create **album-api/src/main/java/com/github/sceneren/album/api/internal/mediastore/MediaStoreQuerySpec.kt**.
- Create **album-api/src/main/java/com/github/sceneren/album/api/internal/mediastore/MediaStoreDataSource.kt**.
- Create **album-api/src/main/java/com/github/sceneren/album/api/internal/mediastore/MediaStoreMediaPagingSource.kt**.
- Create **album-api/src/main/java/com/github/sceneren/album/api/internal/mediastore/AndroidMediaStoreDataSource.kt**.

### album-api Photo Picker

- Create **album-api/src/main/java/com/github/sceneren/album/api/internal/picker/PersistableGrantManager.kt**.
- Create **album-api/src/main/java/com/github/sceneren/album/api/internal/picker/UriMetadataReader.kt**.
- Create **album-api/src/main/java/com/github/sceneren/album/api/internal/picker/PhotoPickerResultProcessor.kt**.
- Create **album-api/src/main/java/com/github/sceneren/album/api/internal/picker/PhotoPickerContractFactory.kt**.
- Create **album-api/src/main/java/com/github/sceneren/album/api/internal/picker/PhotoPickerRegistrar.kt**.
- Create **album-api/src/main/java/com/github/sceneren/album/api/internal/picker/UriAccessChecker.kt**.

### app host

- Modify **app/build.gradle.kts**.
- Modify **app/src/main/AndroidManifest.xml**.
- Replace **app/src/main/java/com/github/sceneren/album/MainActivity.kt**.
- Replace **app/src/main/java/com/github/sceneren/album/AlbumViewModel.kt**.
- Create **app/src/main/java/com/github/sceneren/album/AlbumDataClient.kt**.
- Create **app/src/main/java/com/github/sceneren/album/MediaPermissionRequestFactory.kt**.
- Create **app/src/main/java/com/github/sceneren/album/AlbumScreen.kt**.
- Modify **app/src/main/res/values/strings.xml**.
- Delete obsolete app data/paging files after their replacements compile:
  - **AlbumLoader.kt**
  - **ImageItem.kt**
  - **ImageDirectory.kt**
  - **PagedResult.kt**
  - **FileHelper.kt**
  - **refresh/Footer.kt**
  - **refresh/LoadMoreState.kt**
  - **refresh/RefreshLazyColumn.kt**
  - **refresh/RefreshLazyVerticalGrid.kt**

### Tests

- Create focused tests under **album-api/src/test/java/com/github/sceneren/album/api/** and its internal packages.
- Create **app/src/test/java/com/github/sceneren/album/AlbumViewModelTest.kt**.
- Create **app/src/test/java/com/github/sceneren/album/MediaPermissionRequestFactoryTest.kt**.
- Create **app/src/test/java/com/github/sceneren/album/MainDispatcherRule.kt**.
- Create **app/src/androidTest/java/com/github/sceneren/album/AlbumScreenTest.kt**.

---

### Task 1: Create the Android Library and public media contracts

**Files:**

- Modify: **settings.gradle.kts**
- Modify: **build.gradle.kts**
- Modify: **gradle/libs.versions.toml**
- Create: **album-api/build.gradle.kts**
- Create: **album-api/consumer-rules.pro**
- Create: **album-api/src/main/AndroidManifest.xml**
- Create: all public model files listed above except **AlbumApi.kt**
- Test: **album-api/src/test/java/com/github/sceneren/album/api/AlbumMediaContractsTest.kt**

**Interfaces:**

- Produces: AlbumMediaFilter, AlbumMediaType, AlbumMedia, AlbumDirectory, AlbumMediaSource, MediaAccessStatus, AlbumMediaFeed, PhotoPickResult, PhotoPickFailure, AlbumPhotoPickerLauncher.

- [ ] **Step 1: Add module and dependency catalog wiring**

Add **include(":album-api")** to settings. Add these catalog keys without changing the existing version values:

~~~toml
[versions]
paging = "3.5.0"
room = "2.8.4"
ksp = "2.3.10"
coroutines = "1.11.0"
androidxTestCore = "1.7.0"
robolectric = "4.16.1"

[libraries]
androidx-activity-ktx = { module = "androidx.activity:activity-ktx", version.ref = "activityCompose" }
androidx-paging-runtime = { module = "androidx.paging:paging-runtime", version.ref = "paging" }
androidx-paging-compose = { module = "androidx.paging:paging-compose", version.ref = "paging" }
androidx-paging-testing = { module = "androidx.paging:paging-testing", version.ref = "paging" }
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-paging = { module = "androidx.room:room-paging", version.ref = "room" }
androidx-room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
androidx-test-core-ktx = { module = "androidx.test:core-ktx", version.ref = "androidxTestCore" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
coil-video = { module = "io.coil-kt.coil3:coil-video", version.ref = "coil" }

[plugins]
android-library = { id = "com.android.library", version.ref = "agp" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
~~~

Declare both new plugins with apply false in the root build file. Configure **album-api/build.gradle.kts** as:

~~~kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.github.sceneren.album.api"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(libs.androidx.activity.ktx)
    api(libs.androidx.paging.runtime)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.paging)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.paging.testing)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}

ksp {
    arg(
        "room.schemaLocation",
        project.layout.projectDirectory.dir("schemas").asFile.path,
    )
}
~~~

- [ ] **Step 2: Add the library Manifest without permissions**

~~~xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <application>
        <service
            android:name="com.google.android.gms.metadata.ModuleDependencies"
            android:enabled="false"
            android:exported="false"
            tools:ignore="MissingClass">
            <intent-filter>
                <action android:name="com.google.android.gms.metadata.MODULE_DEPENDENCIES" />
            </intent-filter>
            <meta-data
                android:name="photopicker_activity:0:required"
                android:value="" />
        </service>
    </application>
</manifest>
~~~

Verify the merged library Manifest contains no uses-permission element.

- [ ] **Step 3: Write the failing public-contract test**

~~~kotlin
class AlbumMediaContractsTest {
    @Test
    fun filtersExposeTheThreeApprovedModes() {
        assertEquals(
            listOf(
                AlbumMediaFilter.IMAGES,
                AlbumMediaFilter.VIDEOS,
                AlbumMediaFilter.IMAGES_AND_VIDEOS,
            ),
            AlbumMediaFilter.entries,
        )
    }

    @Test
    fun allDirectoryUsesReservedBucketId() {
        assertEquals(Long.MIN_VALUE, AlbumDirectory.ALL_BUCKET_ID)
    }
}
~~~

- [ ] **Step 4: Run the test and verify it fails**

Run:

~~~powershell
./gradlew.bat :album-api:testDebugUnitTest --tests "com.github.sceneren.album.api.AlbumMediaContractsTest" --console=plain
~~~

Expected: compilation fails because AlbumMediaFilter and AlbumDirectory do not exist.

- [ ] **Step 5: Add the public contracts**

Create the approved declarations:

~~~kotlin
enum class AlbumMediaFilter {
    IMAGES,
    VIDEOS,
    IMAGES_AND_VIDEOS,
}

enum class AlbumMediaType {
    IMAGE,
    VIDEO,
}

enum class AlbumMediaSource {
    MEDIA_STORE,
    PHOTO_PICKER,
}

enum class MediaAccessStatus {
    FULL,
    PARTIAL,
    DENIED,
}

data class AlbumMedia(
    val uri: Uri,
    val mediaType: AlbumMediaType,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val dateAddedEpochSeconds: Long?,
    val dateModifiedEpochSeconds: Long?,
    val width: Int?,
    val height: Int?,
    val durationMillis: Long?,
    val bucketId: Long?,
    val bucketName: String?,
    val selectedAtEpochMillis: Long?,
    val source: AlbumMediaSource,
)

data class AlbumDirectory(
    val bucketId: Long,
    val bucketName: String?,
    val coverUri: Uri,
    val coverMediaType: AlbumMediaType,
    val mediaCount: Long,
) {
    companion object {
        const val ALL_BUCKET_ID: Long = Long.MIN_VALUE
    }
}

data class AlbumMediaFeed(
    val mediaFilter: AlbumMediaFilter,
    val source: AlbumMediaSource,
    val accessStatus: MediaAccessStatus,
    val pagingData: Flow<PagingData<AlbumMedia>>,
)
~~~

Create Photo Picker contracts:

~~~kotlin
interface AlbumPhotoPickerLauncher {
    val mediaFilter: AlbumMediaFilter
    fun launch()
}

sealed interface PhotoPickResult {
    data class Selected(val media: List<AlbumMedia>) : PhotoPickResult
    data object Cancelled : PhotoPickResult
    data class Failed(
        val reason: PhotoPickFailure,
        val cause: Throwable? = null,
    ) : PhotoPickResult
}

enum class PhotoPickFailure {
    SELECTION_LIMIT_EXCEEDED,
    MEDIA_TYPE_NOT_ALLOWED,
    PERSISTABLE_PERMISSION_FAILED,
    METADATA_READ_FAILED,
    DATABASE_WRITE_FAILED,
}
~~~

- [ ] **Step 6: Run contract tests and dependency guard**

Run:

~~~powershell
./gradlew.bat :album-api:testDebugUnitTest --console=plain
./gradlew.bat :album-api:dependencies --configuration debugRuntimeClasspath --console=plain
~~~

Expected: tests pass; dependency output contains no compose, material3, coil, xxpermissions, or devicecompat artifact.

- [ ] **Step 7: Commit only owned hunks**

Stage all new module files and root additions. For **gradle/libs.versions.toml**, stage only the newly added keys; reject the pre-existing changed version lines in the interactive patch.

~~~powershell
git add -- settings.gradle.kts build.gradle.kts album-api
git add -p -- gradle/libs.versions.toml
git commit -m "feat: add album api module contracts"
~~~

### Task 2: Implement filter-aware permission resolution

**Files:**

- Create: **MediaPermissionSnapshot.kt**
- Create: **MediaAccessPolicy.kt**
- Create: **AndroidMediaAccessResolver.kt**
- Test: **album-api/src/test/java/com/github/sceneren/album/api/internal/permission/MediaAccessPolicyTest.kt**

**Interfaces:**

- Consumes: AlbumMediaFilter and MediaAccessStatus.
- Produces: MediaAccessResolver.resolve(mediaFilter): MediaAccessStatus and its AndroidMediaAccessResolver implementation.

- [ ] **Step 1: Write the failing permission matrix tests**

~~~kotlin
class MediaAccessPolicyTest {
    @Test
    fun api34MixedRequiresBothFullPermissions() {
        val imageOnly = snapshot(sdk = 34, images = true)
        assertEquals(
            MediaAccessStatus.PARTIAL,
            MediaAccessPolicy.resolve(AlbumMediaFilter.IMAGES_AND_VIDEOS, imageOnly),
        )
        assertEquals(
            MediaAccessStatus.FULL,
            MediaAccessPolicy.resolve(
                AlbumMediaFilter.IMAGES_AND_VIDEOS,
                snapshot(sdk = 34, images = true, videos = true),
            ),
        )
    }

    @Test
    fun api34VisualSelectionIsPartialForRequestedType() {
        assertEquals(
            MediaAccessStatus.PARTIAL,
            MediaAccessPolicy.resolve(
                AlbumMediaFilter.VIDEOS,
                snapshot(sdk = 34, visualSelected = true),
            ),
        )
    }

    @Test
    fun unrelatedPermissionIsDenied() {
        assertEquals(
            MediaAccessStatus.DENIED,
            MediaAccessPolicy.resolve(
                AlbumMediaFilter.IMAGES,
                snapshot(sdk = 33, videos = true),
            ),
        )
    }

    @Test
    fun legacyReadCoversEveryFilter() {
        AlbumMediaFilter.entries.forEach { filter ->
            assertEquals(
                MediaAccessStatus.FULL,
                MediaAccessPolicy.resolve(filter, snapshot(sdk = 32, legacy = true)),
            )
        }
    }
}
~~~

The private snapshot helper must construct every flag explicitly with false defaults.

- [ ] **Step 2: Run the test and verify it fails**

~~~powershell
./gradlew.bat :album-api:testDebugUnitTest --tests "*.MediaAccessPolicyTest" --console=plain
~~~

Expected: compilation fails because MediaPermissionSnapshot and MediaAccessPolicy do not exist.

- [ ] **Step 3: Implement the pure policy**

~~~kotlin
internal data class MediaPermissionSnapshot(
    val sdkInt: Int,
    val readExternalStorage: Boolean,
    val readMediaImages: Boolean,
    val readMediaVideo: Boolean,
    val readVisualUserSelected: Boolean,
)

internal object MediaAccessPolicy {
    fun resolve(
        filter: AlbumMediaFilter,
        snapshot: MediaPermissionSnapshot,
    ): MediaAccessStatus {
        if (snapshot.sdkInt <= Build.VERSION_CODES.S_V2) {
            return if (snapshot.readExternalStorage) {
                MediaAccessStatus.FULL
            } else {
                MediaAccessStatus.DENIED
            }
        }

        val full = when (filter) {
            AlbumMediaFilter.IMAGES -> snapshot.readMediaImages
            AlbumMediaFilter.VIDEOS -> snapshot.readMediaVideo
            AlbumMediaFilter.IMAGES_AND_VIDEOS ->
                snapshot.readMediaImages && snapshot.readMediaVideo
        }
        if (full) return MediaAccessStatus.FULL

        val oneMixedPermission = filter == AlbumMediaFilter.IMAGES_AND_VIDEOS &&
            (snapshot.readMediaImages || snapshot.readMediaVideo)
        val systemPartial = snapshot.sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            snapshot.readVisualUserSelected

        return if (oneMixedPermission || systemPartial) {
            MediaAccessStatus.PARTIAL
        } else {
            MediaAccessStatus.DENIED
        }
    }
}
~~~

- [ ] **Step 4: Implement the Android snapshot reader**

Define a fakeable boundary, then implement it with application context plus injectable SDK and permission functions:

~~~kotlin
internal fun interface MediaAccessResolver {
    fun resolve(filter: AlbumMediaFilter): MediaAccessStatus
}

internal class AndroidMediaAccessResolver(
    context: Context,
    private val sdkInt: () -> Int = { Build.VERSION.SDK_INT },
    private val isGranted: (String) -> Boolean = { permission ->
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    },
) : MediaAccessResolver {
    override fun resolve(filter: AlbumMediaFilter): MediaAccessStatus {
        val sdk = sdkInt()
        return MediaAccessPolicy.resolve(
            filter,
            MediaPermissionSnapshot(
                sdkInt = sdk,
                readExternalStorage = isGranted(Manifest.permission.READ_EXTERNAL_STORAGE),
                readMediaImages = sdk >= 33 &&
                    isGranted(Manifest.permission.READ_MEDIA_IMAGES),
                readMediaVideo = sdk >= 33 &&
                    isGranted(Manifest.permission.READ_MEDIA_VIDEO),
                readVisualUserSelected = sdk >= 34 &&
                    isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED),
            ),
        )
    }
}
~~~

Do not store a resolved status; resolve from current permission state on every call.

- [ ] **Step 5: Run tests and commit**

~~~powershell
./gradlew.bat :album-api:testDebugUnitTest --tests "*.MediaAccessPolicyTest" --console=plain
git add -- album-api/src/main/java/com/github/sceneren/album/api/internal/permission album-api/src/test/java/com/github/sceneren/album/api/internal/permission
git commit -m "feat: resolve filter-aware media access"
~~~

Expected: all matrix tests pass.

### Task 3: Build Room persistence with stable filtered paging

**Files:**

- Create: all files under **internal/database/**
- Test: **album-api/src/test/java/com/github/sceneren/album/api/internal/database/RoomPickedMediaStoreTest.kt**
- Generate: Room schema version 1 JSON.

**Interfaces:**

- Consumes: AlbumMediaType and AlbumMediaFilter.
- Produces: PickedMediaStore.pagingSource(filter), upsertBatch(drafts), remove(uri), clear(), all().

- [ ] **Step 1: Write failing Room tests**

Use Robolectric with an in-memory database and close it after each test:

~~~kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomPickedMediaStoreTest {
    private lateinit var database: AlbumDatabase
    private lateinit var store: RoomPickedMediaStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AlbumDatabase::class.java,
        ).allowMainThreadQueries().build()
        store = RoomPickedMediaStore(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun batchKeepsPickerOrderAndFiltersByType() = runTest {
        store.upsertBatch(
            listOf(
                draft("content://picked/image", AlbumMediaType.IMAGE, selectedAt = 10),
                draft("content://picked/video", AlbumMediaType.VIDEO, selectedAt = 10),
            ),
        )

        val imagePage = store.pagingSource(AlbumMediaFilter.IMAGES).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        assertEquals(listOf("content://picked/image"), imagePage.data.map { it.uri })
    }

    @Test
    fun duplicateUriUpdatesInsteadOfDuplicating() = runTest {
        store.upsertBatch(listOf(draft("content://picked/same", AlbumMediaType.IMAGE, 10)))
        store.upsertBatch(listOf(draft("content://picked/same", AlbumMediaType.VIDEO, 20)))
        assertEquals(1, store.all().size)
        assertEquals("VIDEO", store.all().single().mediaType)
    }
}
~~~

- [ ] **Step 2: Run the tests and verify they fail**

~~~powershell
./gradlew.bat :album-api:testDebugUnitTest --tests "*.RoomPickedMediaStoreTest" --console=plain
~~~

Expected: compilation fails because AlbumDatabase and RoomPickedMediaStore do not exist.

- [ ] **Step 3: Create entity, DAO, and database**

~~~kotlin
@Entity(tableName = "picked_media")
internal data class PickedMediaEntity(
    @PrimaryKey val uri: String,
    val mediaType: String,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val durationMillis: Long?,
    val selectedAtEpochMillis: Long,
    val sortOrder: Long,
    val ownsPersistableGrant: Boolean,
)

@Dao
internal interface PickedMediaDao {
    @Query(
        "SELECT * FROM picked_media " +
            "WHERE mediaType IN (:mediaTypes) " +
            "ORDER BY sortOrder DESC, uri ASC",
    )
    fun pagingSource(mediaTypes: List<String>): PagingSource<Int, PickedMediaEntity>

    @Query("SELECT * FROM picked_media WHERE uri IN (:uris)")
    suspend fun findByUris(uris: List<String>): List<PickedMediaEntity>

    @Query("SELECT * FROM picked_media ORDER BY sortOrder DESC, uri ASC")
    suspend fun all(): List<PickedMediaEntity>

    @Query("SELECT MAX(sortOrder) FROM picked_media")
    suspend fun maxSortOrder(): Long?

    @Upsert
    suspend fun upsertAll(items: List<PickedMediaEntity>)

    @Query("DELETE FROM picked_media WHERE uri = :uri")
    suspend fun deleteByUri(uri: String): Int

    @Query("DELETE FROM picked_media")
    suspend fun deleteAll(): Int
}

@Database(
    entities = [PickedMediaEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class AlbumDatabase : RoomDatabase() {
    abstract fun pickedMediaDao(): PickedMediaDao
}
~~~

- [ ] **Step 4: Implement transactional store**

Define the draft with entity metadata except sortOrder:

~~~kotlin
internal data class PickedMediaDraft(
    val uri: String,
    val mediaType: String,
    val displayName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val durationMillis: Long?,
    val selectedAtEpochMillis: Long,
    val ownsPersistableGrant: Boolean,
) {
    fun toEntity(
        sortOrder: Long,
        ownsPersistableGrant: Boolean,
    ) = PickedMediaEntity(
        uri = uri,
        mediaType = mediaType,
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        durationMillis = durationMillis,
        selectedAtEpochMillis = selectedAtEpochMillis,
        sortOrder = sortOrder,
        ownsPersistableGrant = ownsPersistableGrant,
    )
}
~~~

Define PickedMediaStore as an internal interface so picker and facade tests can use fakes.

~~~kotlin
internal interface PickedMediaStore {
    fun pagingSource(filter: AlbumMediaFilter): PagingSource<Int, PickedMediaEntity>
    suspend fun upsertBatch(drafts: List<PickedMediaDraft>): List<PickedMediaEntity>
    suspend fun find(uri: String): PickedMediaEntity?
    suspend fun remove(uri: String): PickedMediaEntity?
    suspend fun clear(): List<PickedMediaEntity>
    suspend fun all(): List<PickedMediaEntity>
}
~~~

RoomPickedMediaStore.upsertBatch uses this transaction shape:

~~~kotlin
override suspend fun upsertBatch(
    drafts: List<PickedMediaDraft>,
): List<PickedMediaEntity> = database.withTransaction {
    val existingByUri = dao.findByUris(drafts.map { it.uri })
        .associateBy(PickedMediaEntity::uri)
    val firstSortOrder = (dao.maxSortOrder() ?: 0L) + drafts.size
    val entities = drafts.mapIndexed { index, draft ->
        draft.toEntity(
            sortOrder = firstSortOrder - index,
            ownsPersistableGrant =
                existingByUri[draft.uri]?.ownsPersistableGrant == true ||
                    draft.ownsPersistableGrant,
        )
    }
    dao.upsertAll(entities)
    entities
}
~~~

Read existing rows and MAX(sortOrder), assign **max + drafts.size - index** so the first picker URI sorts first, preserve ownership, upsert once, and return the assigned entities.

Map filters exactly:

~~~kotlin
private fun AlbumMediaFilter.databaseTypes(): List<String> = when (this) {
    AlbumMediaFilter.IMAGES -> listOf("IMAGE")
    AlbumMediaFilter.VIDEOS -> listOf("VIDEO")
    AlbumMediaFilter.IMAGES_AND_VIDEOS -> listOf("IMAGE", "VIDEO")
}
~~~

Add a PickedMediaEntity.toAlbumMedia mapper in RoomPickedMediaStore.kt. Parse the stored URI, map the two stable mediaType strings to AlbumMediaType, copy persisted metadata, set bucket fields and MediaStore dates to null, set selectedAtEpochMillis from the entity, and set source to PHOTO_PICKER. Throw an IllegalStateException for any stored mediaType other than IMAGE or VIDEO so database corruption is visible in tests.

remove and clear must return the deleted entities so grant release can happen after the database mutation.

- [ ] **Step 5: Add ownership, clear, and stable-order tests**

Add tests proving:

- a reselected item retains ownsPersistableGrant true;
- clear returns all deleted entities and leaves the table empty;
- mixed PagingSource order is first picker URI, second picker URI;
- identical sortOrder falls back to uri ASC.

- [ ] **Step 6: Run tests, generate schema, and commit**

~~~powershell
./gradlew.bat :album-api:testDebugUnitTest --tests "*.RoomPickedMediaStoreTest" --console=plain
./gradlew.bat :album-api:kspDebugKotlin --console=plain
git add -- album-api/src/main/java/com/github/sceneren/album/api/internal/database album-api/src/test/java/com/github/sceneren/album/api/internal/database album-api/schemas
git commit -m "feat: persist filtered picker media"
~~~

Expected: Room tests pass and schema version 1 is present.

### Task 4: Define MediaStore queries and PagingSource behavior

**Files:**

- Create: **MediaStoreQuerySpec.kt**
- Create: **MediaStoreDataSource.kt**
- Create: **MediaStoreMediaPagingSource.kt**
- Test: **album-api/src/test/java/com/github/sceneren/album/api/internal/mediastore/MediaStoreQuerySpecTest.kt**
- Test: **album-api/src/test/java/com/github/sceneren/album/api/internal/mediastore/MediaStoreMediaPagingSourceTest.kt**

**Interfaces:**

- Produces: MediaStoreDataSource.loadPage(filter, bucketId, offset, limit), getDirectories(filter), and MediaStoreMediaPagingSource.

- [ ] **Step 1: Write failing query-spec tests**

~~~kotlin
class MediaStoreQuerySpecTest {
    @Test
    fun mixedFilterAndBucketComposeOneSelection() {
        val spec = MediaStoreQuerySpec.create(
            AlbumMediaFilter.IMAGES_AND_VIDEOS,
            bucketId = 42L,
        )

        assertEquals(
            "(media_type IN (?,?)) AND (bucket_id = ?)",
            spec.selection,
        )
        assertEquals(
            listOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                "42",
            ),
            spec.selectionArgs,
        )
    }

    @Test
    fun allBucketOmitsBucketPredicate() {
        val spec = MediaStoreQuerySpec.create(
            AlbumMediaFilter.VIDEOS,
            AlbumDirectory.ALL_BUCKET_ID,
        )
        assertEquals("media_type = ?", spec.selection)
        assertEquals(
            listOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()),
            spec.selectionArgs,
        )
    }
}
~~~

- [ ] **Step 2: Write failing PagingSource tests**

Use a FakeMediaStoreDataSource that records offset and limit:

~~~kotlin
@Test
fun nextPageUsesReturnedOffset() = runTest {
    val fake = FakeMediaStoreDataSource(items = mediaItems(75))
    val source = MediaStoreMediaPagingSource(
        dataSource = fake,
        mediaFilter = AlbumMediaFilter.IMAGES,
        bucketId = AlbumDirectory.ALL_BUCKET_ID,
    )

    val first = source.load(
        PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
    ) as PagingSource.LoadResult.Page
    val second = source.load(
        PagingSource.LoadParams.Append(key = first.nextKey, loadSize = 50, placeholdersEnabled = false),
    ) as PagingSource.LoadResult.Page

    assertEquals(listOf(0, 50), fake.requestedOffsets)
    assertEquals(null, second.nextKey)
}
~~~

- [ ] **Step 3: Run and verify failure**

~~~powershell
./gradlew.bat :album-api:testDebugUnitTest --tests "*.MediaStoreQuerySpecTest" --tests "*.MediaStoreMediaPagingSourceTest" --console=plain
~~~

Expected: compilation fails because the MediaStore query and PagingSource types do not exist.

- [ ] **Step 4: Implement query spec**

~~~kotlin
internal data class MediaStoreQuerySpec(
    val selection: String,
    val selectionArgs: List<String>,
) {
    companion object {
        fun create(
            filter: AlbumMediaFilter,
            bucketId: Long,
        ): MediaStoreQuerySpec {
            val typeClause = when (filter) {
                AlbumMediaFilter.IMAGES -> "media_type = ?" to
                    listOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
                AlbumMediaFilter.VIDEOS -> "media_type = ?" to
                    listOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
                AlbumMediaFilter.IMAGES_AND_VIDEOS -> "media_type IN (?,?)" to
                    listOf(
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                    )
            }
            return if (bucketId == AlbumDirectory.ALL_BUCKET_ID) {
                MediaStoreQuerySpec(typeClause.first, typeClause.second)
            } else {
                MediaStoreQuerySpec(
                    selection = "(" + typeClause.first + ") AND (bucket_id = ?)",
                    selectionArgs = typeClause.second + bucketId.toString(),
                )
            }
        }
    }
}
~~~

- [ ] **Step 5: Implement data-source boundary and PagingSource**

~~~kotlin
internal interface MediaStoreDataSource {
    suspend fun loadPage(
        mediaFilter: AlbumMediaFilter,
        bucketId: Long,
        offset: Int,
        limit: Int,
    ): List<AlbumMedia>

    suspend fun getDirectories(
        mediaFilter: AlbumMediaFilter,
    ): List<AlbumDirectory>
}
~~~

MediaStoreMediaPagingSource uses Int offset keys. Reject a loadSize below 1, call loadPage once, set prevKey to maxOf(0, offset - params.loadSize) unless offset is zero, and set nextKey only when data.size equals params.loadSize. Convert every thrown exception to LoadResult.Error.

Use closestPageToPosition in getRefreshKey:

~~~kotlin
override fun getRefreshKey(state: PagingState<Int, AlbumMedia>): Int? {
    val anchor = state.anchorPosition ?: return null
    val page = state.closestPageToPosition(anchor) ?: return null
    return page.prevKey?.plus(page.data.size)
        ?: page.nextKey?.minus(page.data.size)
}
~~~

- [ ] **Step 6: Run tests and commit**

~~~powershell
./gradlew.bat :album-api:testDebugUnitTest --tests "*.MediaStoreQuerySpecTest" --tests "*.MediaStoreMediaPagingSourceTest" --console=plain
git add -- album-api/src/main/java/com/github/sceneren/album/api/internal/mediastore album-api/src/test/java/com/github/sceneren/album/api/internal/mediastore
git commit -m "feat: define media store paging"
~~~

### Task 5: Implement the Android MediaStore data source and directories

**Files:**

- Create: **AndroidMediaStoreDataSource.kt**
- Test: **album-api/src/test/java/com/github/sceneren/album/api/internal/mediastore/AndroidMediaStoreDataSourceTest.kt**

**Interfaces:**

- Consumes: MediaStoreQuerySpec and MediaStoreDataSource.
- Produces: production ContentResolver-backed page and directory queries.

- [ ] **Step 1: Write failing Robolectric provider tests**

Register a RecordingMediaProvider under authority **media** with ShadowContentResolver. Its query methods return MatrixCursor rows containing _id, media_type, display_name, size, date_added, date_modified, mime_type, width, height, duration, bucket_id, and bucket_display_name.

Test API 30 behavior:

~~~kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AndroidMediaStoreDataSourceTest {
    @Test
    fun api30UsesBundlePagingAndMapsMixedRows() = runTest {
        val provider = RecordingMediaProvider(
            rows = listOf(imageRow(id = 9), videoRow(id = 8, duration = 2_000)),
        )
        ShadowContentResolver.registerProviderInternal("media", provider)
        val source = AndroidMediaStoreDataSource(
            context = ApplicationProvider.getApplicationContext(),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = async {
            source.loadPage(
                AlbumMediaFilter.IMAGES_AND_VIDEOS,
                AlbumDirectory.ALL_BUCKET_ID,
                offset = 10,
                limit = 20,
            )
        }
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(AlbumMediaType.IMAGE, AlbumMediaType.VIDEO), result.await().map { it.mediaType })
        assertEquals(10, provider.lastQueryArgs?.getInt(ContentResolver.QUERY_ARG_OFFSET))
        assertEquals(20, provider.lastQueryArgs?.getInt(ContentResolver.QUERY_ARG_LIMIT))
    }
}
~~~

Add an API 29 test asserting sortOrder ends with **LIMIT 20 OFFSET 10**. Add a directory test asserting the virtual directory is first, mixed counts are correct, and the first sorted media supplies coverUri and coverMediaType.

- [ ] **Step 2: Run and verify failure**

~~~powershell
./gradlew.bat :album-api:testDebugUnitTest --tests "*.AndroidMediaStoreDataSourceTest" --console=plain
~~~

Expected: compilation fails because AndroidMediaStoreDataSource does not exist.

- [ ] **Step 3: Implement projections and page queries**

Use:

~~~kotlin
private val filesUri = MediaStore.Files.getContentUri("external")
private const val sortOrder = "date_added DESC, _id DESC"
~~~

Projection must contain:

~~~kotlin
arrayOf(
    MediaStore.Files.FileColumns._ID,
    MediaStore.Files.FileColumns.MEDIA_TYPE,
    MediaStore.MediaColumns.DISPLAY_NAME,
    MediaStore.MediaColumns.SIZE,
    MediaStore.MediaColumns.DATE_ADDED,
    MediaStore.MediaColumns.DATE_MODIFIED,
    MediaStore.MediaColumns.MIME_TYPE,
    MediaStore.MediaColumns.WIDTH,
    MediaStore.MediaColumns.HEIGHT,
    MediaStore.Video.VideoColumns.DURATION,
    MediaStore.Images.ImageColumns.BUCKET_ID,
    MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME,
)
~~~

On API 30+, pass SQL selection, selection args, sort order, limit, and offset through the query Bundle. On API 24-29, pass selection and args directly and append **LIMIT n OFFSET n** to sortOrder. Wrap queries and cursor mapping in withContext(ioDispatcher) and cursor.use.

Map IMAGE rows to MediaStore.Images.Media.EXTERNAL_CONTENT_URI and VIDEO rows to MediaStore.Video.Media.EXTERNAL_CONTENT_URI through ContentUris.withAppendedId. Map zero/absent optional values to null where they are not reliable. IMAGE duration is always null.

- [ ] **Step 4: Implement directory aggregation**

Run the same filter query without limit/offset, still sorted by date and ID. Aggregate by bucketId in a LinkedHashMap, use the first row as cover, and increment Long counts. Build the virtual ALL_BUCKET_ID directory from the first row with bucketName null. Sort real directories by their first media date descending and bucketId ascending.

Do not query directories for PARTIAL or DENIED here; the facade prevents that call.

- [ ] **Step 5: Run MediaStore tests and commit**

~~~powershell
./gradlew.bat :album-api:testDebugUnitTest --tests "*.AndroidMediaStoreDataSourceTest" --console=plain
git add -- album-api/src/main/java/com/github/sceneren/album/api/internal/mediastore/AndroidMediaStoreDataSource.kt album-api/src/test/java/com/github/sceneren/album/api/internal/mediastore/AndroidMediaStoreDataSourceTest.kt
git commit -m "feat: query visual media from media store"
~~~

### Task 6: Process Photo Picker results atomically

**Files:**

- Create: **PersistableGrantManager.kt**
- Create: **UriMetadataReader.kt**
- Create: **PhotoPickerResultProcessor.kt**
- Test: **album-api/src/test/java/com/github/sceneren/album/api/internal/picker/PhotoPickerResultProcessorTest.kt**

**Interfaces:**

- Consumes: PickedMediaStore and public picker result types.
- Produces: PhotoPickerResultProcessor.process(uris, mediaFilter, maxSelectionCount).

- [ ] **Step 1: Write failing result-processor tests**

Use fakes that record grants, releases, metadata reads, and database writes. Cover:

~~~kotlin
@Test
fun explicitOverflowFailsBeforeTakingGrants() = runTest {
    val result = processor.process(
        uris = listOf(uri("1"), uri("2"), uri("3")),
        mediaFilter = AlbumMediaFilter.IMAGES,
        maxSelectionCount = 2,
    )
    assertEquals(
        PhotoPickResult.Failed(PhotoPickFailure.SELECTION_LIMIT_EXCEEDED),
        result,
    )
    assertTrue(grants.taken.isEmpty())
    assertTrue(store.upsertCalls.isEmpty())
}

@Test
fun databaseFailureReleasesOnlyNewGrants() = runTest {
    grants.persisted += uri("existing")
    store.failure = SQLiteException("write failed")
    val result = processor.process(
        uris = listOf(uri("existing"), uri("new")),
        mediaFilter = AlbumMediaFilter.IMAGES,
        maxSelectionCount = null,
    )
    assertEquals(PhotoPickFailure.DATABASE_WRITE_FAILED, (result as PhotoPickResult.Failed).reason)
    assertEquals(listOf(uri("new")), grants.released)
}
~~~

Also test empty cancellation, URI de-duplication order, MIME mismatch before grants, single/video/mixed success, and existing database ownership preservation.

- [ ] **Step 2: Run and verify failure**

~~~powershell
./gradlew.bat :album-api:testDebugUnitTest --tests "*.PhotoPickerResultProcessorTest" --console=plain
~~~

Expected: compilation fails because the processor boundaries do not exist.

- [ ] **Step 3: Define grant and metadata boundaries**

~~~kotlin
internal interface PersistableGrantManager {
    fun persistedReadUris(): Set<Uri>
    fun takeRead(uri: Uri)
    fun releaseRead(uri: Uri)
}

internal data class PickedUriMetadata(
    val uri: Uri,
    val mediaType: AlbumMediaType,
    val displayName: String?,
    val mimeType: String,
    val sizeBytes: Long?,
    val width: Int?,
    val height: Int?,
    val durationMillis: Long?,
)

internal interface UriMetadataReader {
    fun requiredType(uri: Uri): AlbumMediaType
    fun read(uri: Uri, type: AlbumMediaType): PickedUriMetadata
}

internal class PhotoPickerResultProcessor(
    private val grantManager: PersistableGrantManager,
    private val metadataReader: UriMetadataReader,
    private val store: PickedMediaStore,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun process(
        uris: List<Uri>,
        mediaFilter: AlbumMediaFilter,
        maxSelectionCount: Int?,
    ): PhotoPickResult
}
~~~

- [ ] **Step 4: Implement the processor state machine**

PhotoPickerResultProcessor must:

1. de-duplicate with LinkedHashSet;
2. return Cancelled for an empty list;
3. reject non-positive configured limits in its constructor/caller and reject overflow before grants;
4. resolve every required type and verify it against mediaFilter;
5. snapshot existing persisted read URIs;
6. take grants only for missing URIs and record each new grant;
7. read optional metadata;
8. convert metadata to PickedMediaDraft with current clock millis and new ownership;
9. call store.upsertBatch once;
10. map returned entities to AlbumMedia with source PHOTO_PICKER;
11. on a failure after grants, release only new grants in reverse acquisition order.

Map failures by stage exactly to MEDIA_TYPE_NOT_ALLOWED, PERSISTABLE_PERMISSION_FAILED, METADATA_READ_FAILED, and DATABASE_WRITE_FAILED. Preserve the original throwable as cause. Optional metadata fields may be null; only inability to identify IMAGE/VIDEO is a type failure.

- [ ] **Step 5: Run processor tests and commit**

~~~powershell
./gradlew.bat :album-api:testDebugUnitTest --tests "*.PhotoPickerResultProcessorTest" --console=plain
git add -- album-api/src/main/java/com/github/sceneren/album/api/internal/picker album-api/src/test/java/com/github/sceneren/album/api/internal/picker
git commit -m "feat: persist picker batches atomically"
~~~

### Task 7: Register system Photo Picker launchers without Compose

**Files:**

- Create: **PhotoPickerContractFactory.kt**
- Create: **PhotoPickerRegistrar.kt**
- Create: **UriAccessChecker.kt**
- Extend: **PersistableGrantManager.kt**
- Extend: **UriMetadataReader.kt**
- Test: **album-api/src/test/java/com/github/sceneren/album/api/internal/picker/PhotoPickerContractFactoryTest.kt**
- Test: **album-api/src/test/java/com/github/sceneren/album/api/internal/picker/AndroidPickerAdaptersTest.kt**

**Interfaces:**

- Produces: PickerRegistrar.register(activity, mediaFilter, maxSelectionCount, onResult): AlbumPhotoPickerLauncher and its PhotoPickerRegistrar implementation.

- [ ] **Step 1: Write failing contract mapping tests**

~~~kotlin
class PhotoPickerContractFactoryTest {
    @Test
    fun oneUsesSingleAndNullUsesPlatformCappedMultiple() {
        assertTrue(PhotoPickerContractFactory.create(1) is PickerContract.Single)
        assertTrue(PhotoPickerContractFactory.create(null) is PickerContract.MultipleDefault)
    }

    @Test
    fun explicitMultipleKeepsLimit() {
        assertEquals(
            PickerContract.Multiple(maxItems = 7),
            PhotoPickerContractFactory.create(7),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroIsRejected() {
        PhotoPickerContractFactory.create(0)
    }
}
~~~

Add mapping assertions for ImageOnly, VideoOnly, and ImageAndVideo PickVisualMediaRequest inputs.

- [ ] **Step 2: Run and verify failure**

~~~powershell
./gradlew.bat :album-api:testDebugUnitTest --tests "*.PhotoPickerContractFactoryTest" --console=plain
~~~

Expected: compilation fails because PhotoPickerContractFactory does not exist.

- [ ] **Step 3: Implement Android grant and metadata adapters**

AndroidPersistableGrantManager wraps ContentResolver:

~~~kotlin
override fun takeRead(uri: Uri) {
    resolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION,
    )
}

override fun releaseRead(uri: Uri) {
    resolver.releasePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION,
    )
}
~~~

ContentResolverUriMetadataReader obtains MIME from resolver.getType(uri), accepts only image/ and video/, and queries the URI with a null projection. Read columns by name only when present: DISPLAY_NAME, SIZE, WIDTH, HEIGHT, and DURATION. Do not decode bitmaps or copy files. Return null for unsupported optional columns.

- [ ] **Step 4: Implement contract factory and registrar**

PickerContract is an internal sealed type:

~~~kotlin
internal sealed interface PickerContract {
    data object Single : PickerContract
    data object MultipleDefault : PickerContract
    data class Multiple(val maxItems: Int) : PickerContract
}
~~~

Map it to PickVisualMedia, PickMultipleVisualMedia(), or PickMultipleVisualMedia(maxItems). Convert a single Uri callback to either emptyList or listOf(uri). Build requests with:

~~~kotlin
internal fun AlbumMediaFilter.toPickerRequest(): PickVisualMediaRequest {
    val pickerType = when (this) {
        AlbumMediaFilter.IMAGES ->
            ActivityResultContracts.PickVisualMedia.ImageOnly
        AlbumMediaFilter.VIDEOS ->
            ActivityResultContracts.PickVisualMedia.VideoOnly
        AlbumMediaFilter.IMAGES_AND_VIDEOS ->
            ActivityResultContracts.PickVisualMedia.ImageAndVideo
    }
    return PickVisualMediaRequest.Builder()
        .setMediaType(pickerType)
        .build()
}
~~~

Define the exact registration boundary:

~~~kotlin
internal interface PickerRegistrar {
    fun register(
        activity: ComponentActivity,
        mediaFilter: AlbumMediaFilter,
        maxSelectionCount: Int?,
        onResult: (PhotoPickResult) -> Unit,
    ): AlbumPhotoPickerLauncher
}
~~~

PhotoPickerRegistrar implements it, receives the processor and an application CoroutineScope, registers before STARTED, starts processor work on the application scope, then switches to Dispatchers.Main.immediate and invokes onResult only if the Activity lifecycle is not DESTROYED. The returned launcher exposes the fixed mediaFilter and launches its fixed request.

Define UriAccessChecker and its Android implementation for later reconciliation:

~~~kotlin
internal fun interface UriAccessChecker {
    fun canRead(uri: Uri): Boolean
}

internal class ContentResolverUriAccessChecker(
    private val resolver: ContentResolver,
) : UriAccessChecker {
    override fun canRead(uri: Uri): Boolean = runCatching {
        resolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)
}
~~~

The registrar must not be stored in a process singleton with a strong Activity field; only the returned launcher owns the ActivityResultLauncher.

- [ ] **Step 5: Add adapter tests**

With Robolectric:

- use a fake ContentProvider to return image/video MIME and optional columns;
- verify requiredType rejects application/octet-stream;
- verify releaseRead never requests write permission;
- verify all three filter mappings create the expected PickVisualMedia request type.

- [ ] **Step 6: Run tests and commit**

~~~powershell
./gradlew.bat :album-api:testDebugUnitTest --tests "*.PhotoPickerContractFactoryTest" --tests "*.AndroidPickerAdaptersTest" --console=plain
git add -- album-api/src/main/java/com/github/sceneren/album/api/internal/picker album-api/src/test/java/com/github/sceneren/album/api/internal/picker
git commit -m "feat: register filter-aware photo picker"
~~~

### Task 8: Implement AlbumApi routing and persisted-selection maintenance

**Files:**

- Create: **album-api/src/main/java/com/github/sceneren/album/api/AlbumApi.kt**
- Create: **album-api/src/main/java/com/github/sceneren/album/api/internal/database/AlbumDatabaseFactory.kt**
- Test: **album-api/src/test/java/com/github/sceneren/album/api/AlbumApiTest.kt**

**Interfaces:**

- Produces the complete approved AlbumApi surface.

- [ ] **Step 1: Write failing facade routing tests**

Construct AlbumApi through an internal dependency constructor with fake permission resolver, MediaStoreDataSource, PickedMediaStore, and registrar.

~~~kotlin
@Test
fun fullRoutesToMediaStoreButPartialRoutesToRoom() {
    permissions.result = MediaAccessStatus.FULL
    val full = api.getMediaFeed(AlbumMediaFilter.VIDEOS, pageSize = 25)
    assertEquals(AlbumMediaSource.MEDIA_STORE, full.source)

    permissions.result = MediaAccessStatus.PARTIAL
    val partial = api.getMediaFeed(AlbumMediaFilter.VIDEOS, pageSize = 25)
    assertEquals(AlbumMediaSource.PHOTO_PICKER, partial.source)
    assertEquals(AlbumMediaFilter.VIDEOS, pickedStore.lastPagingFilter)
}

@Test
fun partialDirectoriesAreEmptyWithoutMediaStoreQuery() = runTest {
    permissions.result = MediaAccessStatus.PARTIAL
    assertEquals(emptyList<AlbumDirectory>(), api.getMediaDirectories().getOrThrow())
    assertEquals(0, mediaStore.directoryCalls)
}
~~~

Add tests for DENIED routing, invalid pageSize, remove ownership release, clear, and reconcile stale URI removal.

- [ ] **Step 2: Run and verify failure**

~~~powershell
./gradlew.bat :album-api:testDebugUnitTest --tests "com.github.sceneren.album.api.AlbumApiTest" --console=plain
~~~

Expected: compilation fails because AlbumApi does not exist.

- [ ] **Step 3: Implement database factory**

AlbumDatabaseFactory stores only applicationContext and lazily creates one Room database named **album_api.db** with no destructive fallback:

~~~kotlin
internal object AlbumDatabaseFactory {
    @Volatile
    private var instance: AlbumDatabase? = null

    fun get(context: Context): AlbumDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AlbumDatabase::class.java,
                "album_api.db",
            ).build().also { instance = it }
        }
}
~~~

- [ ] **Step 4: Implement the public facade**

Use the exact public signatures from the approved spec:

~~~kotlin
class AlbumApi internal constructor(
    private val accessResolver: MediaAccessResolver,
    private val mediaStore: MediaStoreDataSource,
    private val pickedStore: PickedMediaStore,
    private val pickerRegistrar: PickerRegistrar,
    private val grantManager: PersistableGrantManager,
    private val uriAccessChecker: UriAccessChecker,
) {
    companion object {
        const val DEFAULT_PAGE_SIZE: Int = 50
        fun create(context: Context): AlbumApi
    }

    fun getMediaAccessStatus(
        mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
    ): MediaAccessStatus

    fun getMediaFeed(
        mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
        bucketId: Long = AlbumDirectory.ALL_BUCKET_ID,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): AlbumMediaFeed

    suspend fun getMediaDirectories(
        mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
    ): Result<List<AlbumDirectory>>

    fun registerPhotoPicker(
        activity: ComponentActivity,
        mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
        maxSelectionCount: Int? = null,
        onResult: (PhotoPickResult) -> Unit,
    ): AlbumPhotoPickerLauncher

    suspend fun removePersistedSelection(uri: Uri): Result<Boolean>
    suspend fun clearPersistedSelections(): Result<Int>
    suspend fun reconcilePersistedSelections(): Result<Int>
}
~~~

Route feeds with:

~~~kotlin
fun getMediaFeed(
    mediaFilter: AlbumMediaFilter,
    bucketId: Long,
    pageSize: Int,
): AlbumMediaFeed {
    require(pageSize > 0) { "pageSize must be > 0" }
    val status = accessResolver.resolve(mediaFilter)
    val config = PagingConfig(
        pageSize = pageSize,
        enablePlaceholders = false,
    )
    val source = if (status == MediaAccessStatus.FULL) {
        AlbumMediaSource.MEDIA_STORE
    } else {
        AlbumMediaSource.PHOTO_PICKER
    }
    val flow = when (source) {
        AlbumMediaSource.MEDIA_STORE -> Pager(config) {
            MediaStoreMediaPagingSource(
                dataSource = mediaStore,
                mediaFilter = mediaFilter,
                bucketId = bucketId,
            )
        }.flow
        AlbumMediaSource.PHOTO_PICKER -> Pager(config) {
            pickedStore.pagingSource(mediaFilter)
        }.flow.map { pagingData ->
            pagingData.map(PickedMediaEntity::toAlbumMedia)
        }
    }
    return AlbumMediaFeed(mediaFilter, source, status, flow)
}
~~~

AlbumMediaFeed stores filter, source, status, and the newly created Pager flow.

getMediaDirectories returns success(emptyList()) unless status is FULL. Wrap actual queries in runCatching.

remove and clear release only entities whose ownsPersistableGrant is true. reconcile compares store.all against persistedReadUris and an injected readability check; remove stale rows and return the count. Return Result.failure if a release fails rather than hiding it.

- [ ] **Step 5: Wire create(context)**

Build AndroidMediaAccessResolver, AndroidMediaStoreDataSource, RoomPickedMediaStore, AndroidPersistableGrantManager, ContentResolverUriMetadataReader, ContentResolverUriAccessChecker, PhotoPickerResultProcessor, an application SupervisorJob scope, and PhotoPickerRegistrar from applicationContext. Do not expose or retain Activity through create.

- [ ] **Step 6: Run the full library test suite and commit**

~~~powershell
./gradlew.bat :album-api:testDebugUnitTest --console=plain
./gradlew.bat :album-api:assembleDebug --console=plain
git add -- album-api/src/main/java/com/github/sceneren/album/api/AlbumApi.kt album-api/src/main/java/com/github/sceneren/album/api/internal/database/AlbumDatabaseFactory.kt album-api/src/test/java/com/github/sceneren/album/api/AlbumApiTest.kt
git commit -m "feat: route album api media feeds"
~~~

### Task 9: Replace app data orchestration with an AlbumApi host adapter

**Files:**

- Modify: **app/build.gradle.kts**
- Create: **AlbumDataClient.kt**
- Create: **MediaPermissionRequestFactory.kt**
- Replace: **AlbumViewModel.kt**
- Test: **MediaPermissionRequestFactoryTest.kt**
- Test: **AlbumViewModelTest.kt**
- Test helper: **MainDispatcherRule.kt**

**Interfaces:**

- Consumes: AlbumApi public contracts.
- Produces: AlbumUiState and Flow<PagingData<AlbumMedia>> for Compose.

- [ ] **Step 1: Add app dependencies without removing old dependencies yet**

Add:

~~~kotlin
implementation(project(":album-api"))
implementation(libs.androidx.paging.compose)
implementation(libs.coil.video)
testImplementation(libs.kotlinx.coroutines.test)
~~~

Do not remove old app dependencies until Task 10 proves old sources are gone.

- [ ] **Step 2: Write failing permission-array tests**

~~~kotlin
class MediaPermissionRequestFactoryTest {
    @Test
    fun api34MixedRequestsBothFullAndPartialPermissions() {
        assertArrayEquals(
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            ),
            MediaPermissionRequestFactory.create(
                AlbumMediaFilter.IMAGES_AND_VIDEOS,
                sdkInt = 34,
            ),
        )
    }

    @Test
    fun api33VideoRequestsOnlyVideo() {
        assertArrayEquals(
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO),
            MediaPermissionRequestFactory.create(AlbumMediaFilter.VIDEOS, 33),
        )
    }

    @Test
    fun api32UsesLegacyRead() {
        assertArrayEquals(
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            MediaPermissionRequestFactory.create(AlbumMediaFilter.IMAGES, 32),
        )
    }
}
~~~

- [ ] **Step 3: Write failing ViewModel transition tests**

Define a FakeAlbumDataClient. Test:

~~~kotlin
@Test
fun changingFilterRebuildsFeedAndResetsDirectory() = runTest {
    val viewModel = AlbumViewModel(fakeClient)
    viewModel.selectDirectory(bucketId = 99)
    viewModel.setMediaFilter(AlbumMediaFilter.VIDEOS)
    advanceUntilIdle()

    assertEquals(AlbumMediaFilter.VIDEOS, viewModel.uiState.value.mediaFilter)
    assertEquals(AlbumDirectory.ALL_BUCKET_ID, viewModel.uiState.value.selectedBucketId)
    assertEquals(AlbumMediaFilter.VIDEOS, fakeClient.lastFeedFilter)
}

@Test
fun partialSourceClearsDirectories() = runTest {
    fakeClient.feedSource = AlbumMediaSource.PHOTO_PICKER
    fakeClient.accessStatus = MediaAccessStatus.PARTIAL
    val viewModel = AlbumViewModel(fakeClient)
    viewModel.refresh()
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.directories.isEmpty())
}
~~~

- [ ] **Step 4: Run tests and verify failure**

~~~powershell
./gradlew.bat :app:testDebugUnitTest --tests "*.MediaPermissionRequestFactoryTest" --tests "*.AlbumViewModelTest" --console=plain
~~~

Expected: compilation fails because the new factory, client, and ViewModel API do not exist.

Create MainDispatcherRule with UnconfinedTestDispatcher, set Dispatchers.Main in starting, and reset it in finished. Add it as a JUnit rule to AlbumViewModelTest so viewModelScope work is deterministic.

- [ ] **Step 5: Implement host adapter and permission factory**

AlbumDataClient exposes only data operations needed by ViewModel:

~~~kotlin
internal interface AlbumDataClient {
    fun getFeed(
        mediaFilter: AlbumMediaFilter,
        bucketId: Long,
    ): AlbumMediaFeed

    suspend fun getDirectories(
        mediaFilter: AlbumMediaFilter,
    ): Result<List<AlbumDirectory>>
}

internal class AlbumApiDataClient(
    private val api: AlbumApi,
) : AlbumDataClient {
    override fun getFeed(
        mediaFilter: AlbumMediaFilter,
        bucketId: Long,
    ) = api.getMediaFeed(mediaFilter, bucketId)

    override suspend fun getDirectories(
        mediaFilter: AlbumMediaFilter,
    ) = api.getMediaDirectories(mediaFilter)
}
~~~

Implement the permission factory exactly:

~~~kotlin
internal object MediaPermissionRequestFactory {
    fun create(
        filter: AlbumMediaFilter,
        sdkInt: Int,
    ): Array<String> {
        if (sdkInt <= Build.VERSION_CODES.S_V2) {
            return arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val fullPermissions = when (filter) {
            AlbumMediaFilter.IMAGES ->
                listOf(Manifest.permission.READ_MEDIA_IMAGES)
            AlbumMediaFilter.VIDEOS ->
                listOf(Manifest.permission.READ_MEDIA_VIDEO)
            AlbumMediaFilter.IMAGES_AND_VIDEOS ->
                listOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                )
        }
        val partialPermission = if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            listOf(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        } else {
            emptyList()
        }
        return (fullPermissions + partialPermission).toTypedArray()
    }
}
~~~

- [ ] **Step 6: Implement ViewModel state and flow replacement**

~~~kotlin
internal data class AlbumUiState(
    val mediaFilter: AlbumMediaFilter = AlbumMediaFilter.IMAGES,
    val accessStatus: MediaAccessStatus = MediaAccessStatus.DENIED,
    val source: AlbumMediaSource = AlbumMediaSource.PHOTO_PICKER,
    val directories: List<AlbumDirectory> = emptyList(),
    val selectedBucketId: Long = AlbumDirectory.ALL_BUCKET_ID,
    val pickerResult: PhotoPickResult? = null,
    val errorMessage: String? = null,
)
~~~

AlbumViewModel holds MutableStateFlow<AlbumUiState> and MutableStateFlow<Flow<PagingData<AlbumMedia>>> initialized with flowOf(PagingData.empty()). Expose mediaPagingData with flatMapLatest and cachedIn(viewModelScope).

Use these method contracts:

~~~kotlin
class AlbumViewModel(
    private val client: AlbumDataClient,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AlbumUiState())
    val uiState: StateFlow<AlbumUiState> = mutableUiState.asStateFlow()

    private val pagingFlow = MutableStateFlow<Flow<PagingData<AlbumMedia>>>(
        flowOf(PagingData.empty()),
    )
    val mediaPagingData: Flow<PagingData<AlbumMedia>> =
        pagingFlow.flatMapLatest { it }.cachedIn(viewModelScope)

    fun refresh()
    fun setMediaFilter(filter: AlbumMediaFilter)
    fun selectDirectory(bucketId: Long)
    fun onPhotoPickResult(result: PhotoPickResult)
}
~~~

refresh obtains a new feed synchronously, compares its source with the previous source, resets bucket to ALL_BUCKET_ID when the source changes, updates source/status, replaces the inner paging flow, and launches getDirectories only for MEDIA_STORE. setMediaFilter returns early for the current value; otherwise it updates the filter, resets bucket, and calls refresh. selectDirectory updates bucket and calls refresh. onPhotoPickResult stores the result and calls refresh after Selected.

Use an internal Factory accepting AlbumDataClient for production ViewModelProvider construction.

- [ ] **Step 7: Run app data tests and commit**

~~~powershell
./gradlew.bat :app:testDebugUnitTest --tests "*.MediaPermissionRequestFactoryTest" --tests "*.AlbumViewModelTest" --console=plain
git add -- app/build.gradle.kts app/src/main/java/com/github/sceneren/album/AlbumDataClient.kt app/src/main/java/com/github/sceneren/album/MediaPermissionRequestFactory.kt app/src/main/java/com/github/sceneren/album/AlbumViewModel.kt app/src/test/java/com/github/sceneren/album
git commit -m "refactor: host album api data in app"
~~~

### Task 10: Replace the app UI, permissions, and obsolete data layer

**Files:**

- Replace: **MainActivity.kt**
- Create: **AlbumScreen.kt**
- Modify: **app/src/main/AndroidManifest.xml**
- Modify: **app/src/main/res/values/strings.xml**
- Modify: **app/build.gradle.kts**
- Delete: obsolete app data and refresh files listed in File Structure.
- Test: **app/src/androidTest/java/com/github/sceneren/album/AlbumScreenTest.kt**

**Interfaces:**

- Consumes: AlbumViewModel, AlbumUiState, AlbumPhotoPickerLauncher, LazyPagingItems<AlbumMedia>.
- Produces: host-only Compose demo for all three filters.

- [ ] **Step 1: Write a failing Compose screen test**

~~~kotlin
@Test
fun screenOffersAllThreeMediaFilters() {
    composeRule.setContent {
        AlbumScreen(
            state = AlbumUiState(),
            media = flowOf(PagingData.empty<AlbumMedia>()).collectAsLazyPagingItems(),
            onFilterChanged = {},
            onRequestPermission = {},
            onOpenPicker = {},
            onDirectorySelected = {},
            onRetry = {},
        )
    }

    composeRule.onNodeWithText("图片").assertExists()
    composeRule.onNodeWithText("视频").assertExists()
    composeRule.onNodeWithText("图片和视频").assertExists()
}
~~~

- [ ] **Step 2: Run compile/test and verify failure**

Run the connected test if a device is available; otherwise compile it:

~~~powershell
./gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain
~~~

Expected: compilation fails because AlbumScreen does not exist.

- [ ] **Step 3: Implement Activity registration before STARTED**

In MainActivity.onCreate:

1. create AlbumApi with application context;
2. create AlbumViewModel through AlbumApiDataClient and its factory;
3. register RequestMultiplePermissions;
4. register three Photo Picker launchers with IMAGES, VIDEOS, and IMAGES_AND_VIDEOS, maxSelectionCount null;
5. forward each PhotoPickResult to viewModel.onPhotoPickResult;
6. configure Coil with AnimatedImageDecoder or GifDecoder plus VideoFrameDecoder;
7. set Compose content and pass callbacks;
8. call viewModel.refresh from onResume.

Select the fixed launcher with:

~~~kotlin
private fun launcherFor(
    filter: AlbumMediaFilter,
): AlbumPhotoPickerLauncher = when (filter) {
    AlbumMediaFilter.IMAGES -> imagePicker
    AlbumMediaFilter.VIDEOS -> videoPicker
    AlbumMediaFilter.IMAGES_AND_VIDEOS -> mixedPicker
}
~~~

Request permissions only from the explicit UI callback:

~~~kotlin
permissionLauncher.launch(
    MediaPermissionRequestFactory.create(
        viewModel.uiState.value.mediaFilter,
        Build.VERSION.SDK_INT,
    ),
)
~~~

- [ ] **Step 4: Implement host Compose UI**

AlbumScreen must:

- render three FilterChip controls;
- show status/source text;
- show a permission button and Photo Picker button;
- show directories only for MEDIA_STORE;
- collect PagingData with collectAsLazyPagingItems;
- use LazyVerticalGrid and item keys based on URI;
- use AsyncImage for content URI;
- show VIDEO badge and formatted duration when present;
- show refresh/append LoadState progress and retry buttons;
- show an empty state after refresh completes with zero items.

Use string resources for all new labels. Keep all Compose and Coil imports in **:app**.

- [ ] **Step 5: Clean the Manifest and dependencies**

Keep:

- INTERNET;
- READ_EXTERNAL_STORAGE with maxSdkVersion 32;
- READ_MEDIA_IMAGES;
- READ_MEDIA_VIDEO;
- READ_MEDIA_VISUAL_USER_SELECTED.

Remove:

- WRITE_EXTERNAL_STORAGE;
- ScopedStorage metadata;
- the app-level ModuleDependencies service and duplicate photopicker metadata, because the library Manifest supplies it.

After obsolete sources are deleted, remove app dependencies on DeviceCompat, XXPermissions, and play-services-base. Keep project(":album-api"), Paging Compose, and coil-video.

- [ ] **Step 6: Delete replaced app implementation**

Delete the nine obsolete data/refresh files listed under File Structure. Search for remaining references:

~~~powershell
Get-ChildItem -Path app/src -Recurse -Filter *.kt | Select-String -Pattern "AlbumLoader|ImageItem|ImageDirectory|PagedResult|FileHelper|XXPermissions|ModuleInstall|RefreshLazy"
~~~

Expected: no output.

- [ ] **Step 7: Run app verification**

~~~powershell
./gradlew.bat :app:testDebugUnitTest --console=plain
./gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain
./gradlew.bat :app:assembleDebug --console=plain
~~~

Expected: all commands pass.

- [ ] **Step 8: Commit the host refactor**

~~~powershell
git add -- app
git commit -m "refactor: make app an album api demo"
~~~

Before committing, inspect the staged diff and ensure no unrelated resource or user change is included.

### Task 11: Regenerate references and perform the final audit

**Files:**

- Modify generated files under **.codex/references/** as produced by the project script.
- Modify **.codex/rules/project_rule.md** only if the generator or new module boundary requires a factual module update.

**Interfaces:**

- Verifies every public and internal deliverable from Tasks 1-10.

- [ ] **Step 1: Regenerate project references**

~~~powershell
python .codex/scripts/gen_references.py
python .codex/scripts/gen_references.py --diff
~~~

Expected: scan lists **:app** and **:album-api**, and the module dependency map shows app depending on album-api.

- [ ] **Step 2: Run focused review and performance skills**

Use **.codex/skills/code_review/SKILL.md** because more than two source/config files changed. Use **.codex/skills/performance_check/SKILL.md** because MediaStore, Paging, Room, URI metadata, and a Compose lazy grid changed. Resolve all high-confidence correctness, permission, cursor, ANR, memory, and paging findings before continuing.

- [ ] **Step 3: Run the complete verification matrix**

~~~powershell
./gradlew.bat :album-api:testDebugUnitTest --console=plain
./gradlew.bat :app:testDebugUnitTest --console=plain
./gradlew.bat :album-api:lintDebug :app:lintDebug --console=plain
./gradlew.bat :album-api:assembleDebug :app:assembleDebug --console=plain
./gradlew.bat :album-api:dependencies --configuration debugRuntimeClasspath --console=plain
~~~

Expected:

- all tests, lint, and builds pass;
- album-api runtime dependencies contain no Compose, Material3, Coil, XXPermissions, or DeviceCompat;
- app contains the UI-only integrations.

- [ ] **Step 4: Run device verification when available**

~~~powershell
./gradlew.bat :app:connectedDebugAndroidTest --console=plain
~~~

Manually verify Android 14+ FULL/PARTIAL/DENIED, Android 13 image/video permission combinations, API 24-32 legacy permission, all three picker filters, explicit and default multi-select, and process restart URI access. If no device is available, record this command as not run rather than claiming it passed.

- [ ] **Step 5: Audit repository state**

~~~powershell
git diff --check
git status --short
~~~

Expected: no whitespace errors; only intentionally generated reference files plus the user’s pre-existing **gradle/libs.versions.toml** version-line changes and **.codex/scripts/__pycache__/** remain outside implementation commits.

- [ ] **Step 6: Commit reference updates**

~~~powershell
git add -- .codex/references .codex/rules/project_rule.md
git commit -m "docs: refresh album module references"
~~~

Stage only files that actually changed. Never stage **.codex/scripts/__pycache__/**.

---

## Spec Coverage Map

- Module boundary and no-UI dependency rule: Tasks 1 and 11.
- Unified IMAGES/VIDEOS/IMAGES_AND_VIDEOS public contracts: Tasks 1, 4, 7, 9, and 10.
- Filter-relative FULL/PARTIAL/DENIED permission policy: Task 2.
- FULL MediaStore and PARTIAL/DENIED Room routing: Task 8.
- MediaStore.Files cross-version paging and directories: Tasks 4 and 5.
- Photo Picker single/multiple, default cap semantics, MIME validation, durable URI grants, and atomic batches: Tasks 6 and 7.
- Room type filtering, stable order, remove/clear/reconcile: Tasks 3 and 8.
- Compose-free album-api and host-only Compose demo: Tasks 1 and 10.
- Permission refresh on callback/onResume and filter changes: Tasks 9 and 10.
- Unit, Robolectric, Compose, build, lint, dependency, reference, and device checks: Tasks 1-11.
