val intellijUltimateBuildVersion: String by project
val ultimateHome: String? = System.getenv("ULTIMATE_HOME")

plugins {
    kotlin("jvm")
}

intellij {
    if (!ultimateHome.isNullOrBlank()) {
        println("Using local installation of Ultimate: $ultimateHome")
        localPath.set(ultimateHome)
        localSourcesPath.set(ultimateHome)
    } else {
        println("No local installation of Ultimate found, using version $intellijUltimateBuildVersion")
        version.set(intellijUltimateBuildVersion)
    }
    plugins.set(listOf("JavaScript"))
}

dependencies {
    implementation(project(":common"))
}
