val intellijUltimateBuildVersion: String by project
val ideaHome: String? = System.getenv("IDEA_HOME")

plugins {
    kotlin("jvm")
}

intellij {
    if (!ideaHome.isNullOrBlank()) {
        localPath.set(ideaHome)
        localSourcesPath.set(ideaHome)
    } else {
        version.set(intellijUltimateBuildVersion)
    }
    plugins.set(listOf("JavaScript"))
}

dependencies {
    implementation(project(":common"))
}
