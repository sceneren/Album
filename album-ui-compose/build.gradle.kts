plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = providers.gradleProperty("albumUiComposeNamespace").get()
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

    buildFeatures { compose = true }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(project(":album-api"))
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.animation)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.paging.compose)
    implementation(libs.zoomimage.compose)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
