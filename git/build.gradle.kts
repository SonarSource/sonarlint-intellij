plugins {
    kotlin("jvm")
}

val intellijBuildVersion: String by project
val sonarlintCoreVersion: String by project

intellij {
    version.set(intellijBuildVersion)
    plugins.set(listOf("Git4Idea"))
}

dependencies {
    implementation(project(":common"))
}
