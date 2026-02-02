import java.net.URI
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.10.5"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.gradle.develocity") version "4.0"
    id("com.gradle.common-custom-user-data-gradle-plugin") version "2.2.1"
}

rootProject.name = "sonarlint-intellij"
val artifactoryUrl = System.getenv("ARTIFACTORY_URL") ?: (extra["artifactoryUrl"] as? String ?: "")
val artifactoryUsername = System.getenv("ARTIFACTORY_ACCESS_USERNAME") ?: (extra["artifactoryUsername"] as? String ?: "")
val artifactoryPassword = System.getenv("ARTIFACTORY_ACCESS_TOKEN") ?: (extra["artifactoryPassword"] as? String ?: "")

dependencyResolutionManagement {

    repositoriesMode = RepositoriesMode.PREFER_SETTINGS

    @Suppress("UnstableApiUsage")
    repositories {
        if (artifactoryUrl.isNotEmpty() && artifactoryUsername.isNotEmpty() && artifactoryPassword.isNotEmpty()) {
            maven("$artifactoryUrl/sonarsource") {
                credentials {
                    username = artifactoryUsername
                    password = artifactoryPassword
                }
            }
            intellijPlatform {
                localPlatformArtifacts()
                maven("$artifactoryUrl/jetbrains-intellij-dependencies") {
                    name = "IntelliJ Platform Dependencies Repository"
                    credentials {
                        username = artifactoryUsername
                        password = artifactoryPassword
                    }
                }
                ivy {
                    name = "JetBrains IDE Installers"
                    url = URI("$artifactoryUrl/jetbrains-download")
                    credentials {
                        username = artifactoryUsername
                        password = artifactoryPassword
                    }
                    patternLayout {
                        artifact("[organization]/[module]-[revision](-[classifier]).[ext]",)
                        artifact("[organization]/[module]-[revision](.[classifier]).[ext]")
                        artifact("[organization]/[revision]/[module]-[revision](-[classifier]).[ext]")
                        artifact("[organization]/[revision]/[module]-[revision](.[classifier]).[ext]")
                        artifact("[organization]/[module]-[revision]-[classifier].tar.gz")
                    }
                    content {
                        IntelliJPlatformType.values()
                            .filter { it != IntelliJPlatformType.AndroidStudio }
                            .mapNotNull { it.installer }
                            .forEach {
                                includeModule(it.groupId, it.artifactId)
                            }
                    }
                    metadataSources { artifact() }
                }
                ivy {
                    name = "Android Studio Installers"
                    url = URI("$artifactoryUrl/android-studio")
                    credentials {
                        username = artifactoryUsername
                        password = artifactoryPassword
                    }
                    patternLayout {
                        artifact("/ide-zips/[revision]/[artifact]-[revision]-[classifier].[ext]")
                        artifact("/install/[revision]/[artifact]-[revision]-[classifier].[ext]")
                    }
                    content {
                        val coordinates = IntelliJPlatformType.AndroidStudio.installer
                        requireNotNull(coordinates)

                        includeModule(coordinates.groupId, coordinates.artifactId)
                    }
                    metadataSources { artifact() }
                }
                maven("$artifactoryUrl/intellij-releases") {
                    name = "IntelliJ Repository (Releases)"
                    credentials {
                        username = artifactoryUsername
                        password = artifactoryPassword
                    }
                }
            }
        } else {
            mavenCentral()
            intellijPlatform {
                localPlatformArtifacts()
                jetbrainsIdeInstallers()
                androidStudioInstallers()
                intellijDependencies()
                releases()
            }
        }
    }
}

include("its", "clion", "clion-resharper", "nodejs", "common", "git", "rider", "test-common")

val isCiServer = System.getenv("CI") != null

buildCache {
    local {
        isEnabled = !isCiServer
    }
    remote(develocity.buildCache) {
        isEnabled = true
        isPush = isCiServer
    }
}

develocity {
    server = "https://develocity-public.sonar.build"
    buildScan {
        publishing.onlyIf { isCiServer && it.isAuthenticated }
        capture {
            buildLogging.set(!startParameter.taskNames.contains("properties"))
        }
    }
}
