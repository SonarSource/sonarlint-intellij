rootProject.name = "sonarlint-intellij"
include("its", "clion", "clion-resharper", "nodejs", "clion-common", "common", "git", "rider")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
    id("com.gradle.develocity") version "3.18.2"
    id("com.gradle.common-custom-user-data-gradle-plugin") version "2.2.1"
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            val junit5 = version("junit5", "5.9.2")
            library("junit-api", "org.junit.jupiter", "junit-jupiter-api").versionRef(junit5)
            library("junit-engine", "org.junit.jupiter", "junit-jupiter-engine").versionRef(junit5)
            library("assertj-core", "org.assertj:assertj-core:3.23.1")
            library("mockito-core", "org.mockito:mockito-core:3.10.0")
        }
    }
}

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
        capture {
            buildLogging.set(!startParameter.taskNames.contains("properties"))
        }
    }
}
