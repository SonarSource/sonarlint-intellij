rootProject.name = "sonarlint-intellij"
include("its", "clion", "common", "git", "rider")

pluginManagement {
    repositories {
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        gradlePluginPortal()
    }
}
