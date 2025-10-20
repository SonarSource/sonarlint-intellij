import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.10.1"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("com.gradle.develocity") version "3.19.2"
    id("com.gradle.common-custom-user-data-gradle-plugin") version "2.2.1"
}

rootProject.name = "sonarlint-intellij"

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

    @Suppress("UnstableApiUsage")
    repositories {
        maven("https://repox.jfrog.io/repox/sonarsource") {
            credentials {
                username = System.getenv("ARTIFACTORY_PRIVATE_USERNAME") ?: (extra["artifactoryUsername"] as? String ?: "")
                password = System.getenv("ARTIFACTORY_PRIVATE_PASSWORD") ?: (extra["artifactoryPassword"] as? String ?: "")
            }
        }
        mavenCentral()
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
    server = "https://develocity.sonar.build"
    buildScan {
        publishing.onlyIf { isCiServer && it.isAuthenticated }
        capture {
            buildLogging.set(!startParameter.taskNames.contains("properties"))
        }
    }
}
