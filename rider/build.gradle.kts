val riderBuildVersion: String by project
val riderHome: String? = System.getenv("RIDER_HOME")

plugins {
    kotlin("jvm")
}

intellij {
    if (!riderHome.isNullOrBlank()) {
        localPath.set(riderHome)
        localSourcesPath.set(riderHome)
    } else {
        version.set(riderBuildVersion)
    }
}

dependencies {
    implementation(project(":common"))
    compileOnly(libs.findbugs.jsr305)
}
