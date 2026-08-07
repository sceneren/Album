import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val publishedGroup = providers.environmentVariable("GROUP")
    .orElse(providers.gradleProperty("publishedGroup"))
    .map { group ->
        val artifact = providers.environmentVariable("ARTIFACT")
            .orElse(providers.gradleProperty("publishedArtifact"))
            .get()
        "$group.$artifact"
    }
val publishedVersion = providers.environmentVariable("VERSION")
    .orElse(providers.gradleProperty("publishedVersion"))

subprojects {
    group = publishedGroup.get()
    version = publishedVersion.get()

    pluginManager.withPlugin("com.android.library") {
        pluginManager.apply("maven-publish")

        extensions.configure<LibraryExtension> {
            publishing {
                singleVariant("release") {
                    withSourcesJar()
                }
            }
        }

        afterEvaluate {
            extensions.configure<PublishingExtension> {
                publications {
                    register<MavenPublication>("release") {
                        from(components["release"])
                        artifactId = project.name
                        pom {
                            name.set(project.name)
                            description.set("Album Android 相册选择器的 ${project.name} 模块")
                            url.set("https://github.com/sceneren/Album")
                            scm {
                                connection.set("scm:git:https://github.com/sceneren/Album.git")
                                developerConnection.set("scm:git:ssh://git@github.com/sceneren/Album.git")
                                url.set("https://github.com/sceneren/Album")
                            }
                        }
                    }
                }
            }
        }
    }
}
