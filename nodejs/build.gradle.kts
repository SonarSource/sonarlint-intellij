val intellijUltimateBuildVersion: String by project
val ultimateHome: String? = System.getenv("ULTIMATE_HOME")

plugins {
    id("org.jetbrains.intellij.platform.module")
    kotlin("jvm")
}

dependencies {
    intellijPlatform {
        if (!ultimateHome.isNullOrBlank()) {
            println("Using local installation of Ultimate: $ultimateHome")
            local(ultimateHome)
        } else {
            println("No local installation of Ultimate found, using version $intellijUltimateBuildVersion")
            intellijIdeaUltimate(intellijUltimateBuildVersion)
        }
        bundledPlugins("JavaScript")
    }
    implementation(project(":common"))
}
