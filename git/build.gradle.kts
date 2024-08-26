val intellijBuildVersion: String by project
val ideaHome: String? = System.getenv("IDEA_HOME")

plugins {
    kotlin("jvm")
}

intellij {
    if (ideaHome != null) {
        localPath.set(ideaHome)
        localSourcesPath.set(ideaHome)
    } else {
        version.set(intellijBuildVersion)
    }
    plugins.set(listOf("Git4Idea"))
}

dependencies {
    implementation(project(":common"))
}
