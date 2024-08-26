val clionResharperBuildVersion: String by project
val resharperHome: String? = System.getenv("RESHARPER_HOME")

plugins {
    kotlin("jvm")
}

intellij {
    if (resharperHome != null) {
        localPath.set(resharperHome)
        localSourcesPath.set(resharperHome)
    } else {
        version.set(clionResharperBuildVersion)
    }
}

dependencies {
    implementation(project(":common"))
    testImplementation(libs.junit.api)
    testImplementation(libs.mockito.core)
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    // Needed for https://github.com/gradle/gradle/issues/22333
    testRuntimeOnly(libs.junit.launcher)
    testRuntimeOnly(libs.junit.engine)
    compileOnly(libs.findbugs.jsr305)
}

tasks.test {
    useJUnitPlatform()
}
