val clionBuildVersion: String by project

plugins {
    id("org.jetbrains.intellij.platform.module")
}

dependencies {
    intellijPlatform {
        clion(clionBuildVersion)
        bundledPlugin("com.intellij.clion")
        bundledPlugin("com.intellij.cidr.base")
        bundledPlugin("com.intellij.cidr.lang")
        instrumentationTools()
    }
    implementation(project(":common"))
    implementation(project(":clion-common"))
    testImplementation(libs.junit.api)
    testImplementation(libs.mockito.core)
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    // Needed for https://github.com/gradle/gradle/issues/22333
    testRuntimeOnly(libs.junit.launcher)
    testRuntimeOnly(libs.junit.engine)
}

tasks.test {
    useJUnitPlatform()
}
