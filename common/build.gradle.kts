plugins {
    kotlin("jvm")
}

val sonarlintCoreVersion: String by project
val intellijBuildVersion: String by project

intellij {
    version.set(intellijBuildVersion)
}
