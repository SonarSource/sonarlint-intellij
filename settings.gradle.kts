rootProject.name = "sonarlint-intellij"
include("its", "clion", "common", "git", "goland", "rider")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            val junit5 = version("junit5", "5.9.2")
            library("junit-api", "org.junit.jupiter", "junit-jupiter-api").versionRef(junit5)
            library("junit-engine", "org.junit.jupiter", "junit-jupiter-engine").versionRef(junit5)
            // Needed for https://github.com/gradle/gradle/issues/22333
            library("junit-launcher", "org.junit.platform:junit-platform-launcher:1.9.2")
            library("assertj-core", "org.assertj:assertj-core:3.23.1")
            library("mockito-core", "org.mockito:mockito-core:3.10.0")
        }
    }
}
