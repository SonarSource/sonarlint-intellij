val intellijUltimateBuildVersion: String by project
val ultimateHome: String? = System.getenv("ULTIMATE_HOME")

plugins {
    kotlin("jvm")
}

intellij {
    if (!ultimateHome.isNullOrBlank()) {
        localPath.set(ultimateHome)
        localSourcesPath.set(ultimateHome)
    } else {
        version.set(intellijUltimateBuildVersion)
    }
    plugins.set(listOf("JavaScript"))
}

dependencies {
    implementation(project(":common"))
}
