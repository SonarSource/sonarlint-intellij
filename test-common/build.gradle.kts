import org.jetbrains.intellij.platform.gradle.TestFrameworkType

val intellijBuildVersion: String by project
val ideaHome: String? = System.getenv("IDEA_HOME")

plugins {
    id("org.jetbrains.intellij.platform.module")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.license)
    `java-test-fixtures`
}

apply(from = "${rootProject.projectDir}/gradle/module-conventions.gradle")

dependencies {
    intellijPlatform {
        if (!ideaHome.isNullOrBlank()) {
            local(ideaHome)
        } else {
            intellijIdeaCommunity(intellijBuildVersion)
        }
        pluginComposedModule(implementation(project(":common")))
        testFramework(TestFrameworkType.Platform)
    }
    testFixturesImplementation(libs.junit.jupiter)
    testFixturesImplementation(libs.junit.four)
    testFixturesImplementation(libs.mockito.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
