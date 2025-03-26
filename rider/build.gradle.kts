val riderBuildVersion: String by project
val riderHome: String? = System.getenv("RIDER_HOME")

plugins {
    id("org.jetbrains.intellij.platform.module")
    kotlin("jvm")
}

dependencies {
    intellijPlatform {
        if (!riderHome.isNullOrBlank()) {
            println("Using local installation of Rider: $riderHome")
            local(riderHome)
        } else {
            println("No local installation of Rider found, using version $riderBuildVersion")
            rider(riderBuildVersion, useInstaller = false)
        }
        bundledPlugins("Git4Idea")
    }
    implementation(project(":common"))
    implementation(project(":git"))
    compileOnly(libs.findbugs.jsr305)
}
