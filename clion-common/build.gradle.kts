import org.jetbrains.intellij.platform.gradle.TestFrameworkType

val clionResharperBuildVersion: String by project
val resharperHome: String? = System.getenv("RESHARPER_HOME")

plugins {
    id("org.jetbrains.intellij.platform.module")
    kotlin("jvm")
}

dependencies {
    intellijPlatform {
        if (!resharperHome.isNullOrBlank()) {
            println("Using local installation of CLion: $resharperHome")
            local(resharperHome)
        } else {
            println("No local installation of CLion found, using version $clionResharperBuildVersion")
            rider(clionResharperBuildVersion, useInstaller = false)
        }
        testFramework(TestFrameworkType.Platform)
    }
    implementation(project(":common"))
    testImplementation(libs.junit.api)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.engine)
    compileOnly(libs.findbugs.jsr305)
}

tasks.compileJava {
    options.isIncremental = true
}

tasks.test {
    useJUnitPlatform()
}
