rootProject.name = "sonarlint-intellij"
include(":its", ":clion", ":clion-resharper", ":nodejs", ":clion-common", ":common", ":git", ":rider")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.9.0")
    id("com.gradle.develocity") version("3.18.2")
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

val isCiServer = System.getenv()["CI"] != null
val isMasterBranch = System.getenv()["CIRRUS_BRANCH"] == "master"
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
        if (isCiServer) {
            uploadInBackground.set(false)
            tag("CI")
            for (key in listOf(
                "CIRRUS_BUILD_ID",
                "CIRRUS_TASK_ID",
                "CIRRUS_TASK_NAME",
                "CIRRUS_BRANCH",
                "CIRRUS_CHANGE_IN_REPO"
            )) {
                value(key, System.getenv(key))
            }
        }
    }
}
