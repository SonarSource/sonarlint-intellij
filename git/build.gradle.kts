val intellijBuildVersion: String by project

plugins {
    kotlin("jvm")
}

intellij {
    version.set(intellijBuildVersion)
    plugins.set(listOf("Git4Idea"))
}

dependencies {
    implementation(project(":common"))
}
