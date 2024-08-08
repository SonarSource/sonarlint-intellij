val intellijBuildVersion: String by project

plugins {
    id("org.jetbrains.intellij.platform.module")
    kotlin("jvm")
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(intellijBuildVersion)
        instrumentationTools()
    }
}
