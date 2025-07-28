val intellijBuildVersion: String by project
val ideaHome: String? = System.getenv("IDEA_HOME")

plugins {
    kotlin("jvm")
}

intellij {
    if (!ideaHome.isNullOrBlank()) {
        localPath.set(ideaHome)
        localSourcesPath.set(ideaHome)
    } else {
        version.set(intellijBuildVersion)
    }
}


dependencies {
    implementation(project(":common"))
    implementation(platform(libs.junit.bom))
    implementation("org.junit.jupiter:junit-jupiter")
    implementation(libs.mockito.core)
}
