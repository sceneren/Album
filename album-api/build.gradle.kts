plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = providers.gradleProperty("albumApiNamespace").get()
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
    api(libs.androidx.activity.ktx) {
        exclude(group = "androidx.compose.runtime", module = "runtime-annotation")
    }
    api(libs.androidx.paging.runtime)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.luban)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.paging.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
