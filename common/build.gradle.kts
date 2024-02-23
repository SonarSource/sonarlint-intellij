val intellijBuildVersion: String by project

plugins {
    kotlin("jvm")
}

intellij {
    version.set(intellijBuildVersion)
}

dependencies {
    implementation(libs.sonarlint.core)
}
