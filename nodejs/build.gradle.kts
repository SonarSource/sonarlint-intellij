val intellijUltimateBuildVersion: String by project
val ultimateHome: String? = System.getenv("ULTIMATE_HOME")

plugins {
    id("org.jetbrains.intellij.platform.module")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.license)
}

apply(from = "${rootProject.projectDir}/gradle/module-conventions.gradle")

dependencies {
    intellijPlatform {
        if (!ultimateHome.isNullOrBlank()) {
            println("Using local installation of Ultimate: $ultimateHome")
            local(ultimateHome)
        } else {
            println("No local installation of Ultimate found, using version $intellijUltimateBuildVersion")
            intellijIdeaUltimate(intellijUltimateBuildVersion)
        }
        pluginComposedModule(implementation(project(":common")))
        bundledPlugins("JavaScript")
    }
}
