import org.jetbrains.intellij.platform.gradle.TestFrameworkType

val intellijBuildVersion: String by project
val ideaHome: String? = System.getenv("IDEA_HOME")

plugins {
    id("org.jetbrains.intellij.platform.module")
    kotlin("jvm")
}

dependencies {
    intellijPlatform {
        if (!ideaHome.isNullOrBlank()) {
            local(ideaHome)
        } else {
            intellijIdeaCommunity(intellijBuildVersion)
        }
        bundledPlugins("Git4Idea")
        testFramework(TestFrameworkType.Platform)
    }
    implementation(project(":common"))
}
