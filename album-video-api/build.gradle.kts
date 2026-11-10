plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = providers.gradleProperty("albumVideoApiNamespace").get()
    compileSdk {
        version = release(providers.gradleProperty("androidCompileSdk").get().toInt())
    }
    defaultConfig {
        minSdk = providers.gradleProperty("androidMinSdk").get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        val javaVersion = JavaVersion.toVersion(providers.gradleProperty("androidJavaVersion").get())
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }
}
