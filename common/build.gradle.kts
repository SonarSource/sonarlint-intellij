val intellijBuildType: String by project
val intellijBuildVersion: String by project
val ideaHome: String? = System.getenv("IDEA_HOME")

plugins {
    id("org.jetbrains.intellij.platform.module")
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.kotlin)
}

apply(from = "${rootProject.projectDir}/gradle/module-conventions.gradle")

dependencies {
    intellijPlatform {
        if (!ideaHome.isNullOrBlank()) {
            local(ideaHome)
        } else {
            create(intellijBuildType, intellijBuildVersion)
        }
    }
    compileOnly(libs.findbugs.jsr305)
}
