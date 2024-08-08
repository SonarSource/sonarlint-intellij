val clionResharperBuildVersion: String by project

plugins {
    id("org.jetbrains.intellij.platform.module")
    kotlin("jvm")
}

dependencies {
    intellijPlatform {
        rider(clionResharperBuildVersion)
        instrumentationTools()
    }

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
