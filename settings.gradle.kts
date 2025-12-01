import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.10.4"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.gradle.develocity") version "4.0"
    id("com.gradle.common-custom-user-data-gradle-plugin") version "2.2.1"
}

rootProject.name = "sonarlint-intellij"
val artifactoryUrl = System.getenv("ARTIFACTORY_URL") ?: (extra["artifactoryUrl"] as? String ?: "")
val artifactoryUsername = System.getenv("ARTIFACTORY_ACCESS_USERNAME") ?: (extra["artifactoryUsername"] as? String ?: "")
val artifactoryPassword = System.getenv("ARTIFACTORY_ACCESS_TOKEN") ?: (extra["artifactoryPassword"] as? String ?: "")

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

    @Suppress("UnstableApiUsage")
    repositories {
        if (artifactoryUrl.isNotEmpty() && artifactoryUsername.isNotEmpty() && artifactoryPassword.isNotEmpty()) {
            maven("$artifactoryUrl/sonarsource") {
                credentials {
                    username = artifactoryUsername
                    password = artifactoryPassword
                }
            }
        } else {
            mavenCentral()
        }
        intellijPlatform {
            defaultRepositories()
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
