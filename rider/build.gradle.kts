val riderBuildVersion: String by project

plugins {
    id("org.jetbrains.intellij.platform.module")
    kotlin("jvm")
}

dependencies {
    intellijPlatform {
        rider(riderBuildVersion)
        instrumentationTools()
    }

    implementation(project(":common"))
    compileOnly(libs.findbugs.jsr305)
}
