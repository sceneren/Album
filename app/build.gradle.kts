plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val publishedVersion = providers.environmentVariable("VERSION")
    .orElse(providers.gradleProperty("publishedVersion"))

android {
    namespace = providers.gradleProperty("appNamespace").get()
    compileSdk {
        version = release(providers.gradleProperty("androidCompileSdk").get().toInt())
    }

    defaultConfig {
        applicationId = providers.gradleProperty("appApplicationId").get()
        minSdk = providers.gradleProperty("androidMinSdk").get().toInt()
        targetSdk = providers.gradleProperty("androidTargetSdk").get().toInt()
        versionCode = providers.gradleProperty("appVersionCode").get().toInt()
        versionName = publishedVersion.get()

        testInstrumentationRunner = providers.gradleProperty("androidTestInstrumentationRunner").get()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        val javaVersion = JavaVersion.toVersion(providers.gradleProperty("androidJavaVersion").get())
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":album-api"))
    implementation(project(":album-ui-view"))
    implementation(project(":album-ui-compose"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.compose.viewmodel)

    implementation(libs.coil.compose)
    implementation(libs.coil)
    implementation(libs.coil.gif)
    implementation(libs.androidx.paging.compose)
    implementation(libs.coil.video)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
