plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = providers.gradleProperty("albumVideoMedia3Namespace").get()
    compileSdk {
        version = release(providers.gradleProperty("androidCompileSdk").get().toInt())
    }

    defaultConfig {
        minSdk = providers.gradleProperty("androidMinSdk").get().toInt()
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = providers.gradleProperty("androidTestInstrumentationRunner").get()
    }

    compileOptions {
        val javaVersion = JavaVersion.toVersion(providers.gradleProperty("androidJavaVersion").get())
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(project(":album-video-api"))
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.robolectric)
}
