val riderBuildVersion: String by project

plugins {
    kotlin("jvm")
}

intellij {
    version.set(riderBuildVersion)
}

dependencies {
    implementation(project(":common"))
    compileOnly(libs.findbugs.jsr305)
}
