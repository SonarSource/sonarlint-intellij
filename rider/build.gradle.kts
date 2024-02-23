val riderBuildVersion: String by project

plugins {
    kotlin("jvm")
}

intellij {
    version.set(riderBuildVersion)
}

dependencies {
    implementation(project(":common"))
    implementation(libs.sonarlint.core)
}

