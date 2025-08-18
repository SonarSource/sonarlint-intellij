import org.jetbrains.intellij.platform.gradle.TestFrameworkType

val clionBuildVersion: String by project
val clionHome: String? = System.getenv("CLION_HOME")

plugins {
    id("org.jetbrains.intellij.platform.module")
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.license)
    alias(libs.plugins.kotlin)
}

apply(from = "${rootProject.projectDir}/gradle/module-conventions.gradle")

dependencies {
    intellijPlatform {
        if (!clionHome.isNullOrBlank()) {
            println("Using local installation of CLion: $clionHome")
            local(clionHome)
        } else {
            println("No local installation of CLion found, using version $clionBuildVersion")
            clion(clionBuildVersion)
        }
        pluginComposedModule(implementation(project(":common")))
        // nativeDebug: https://youtrack.jetbrains.com/issue/CPP-43231/Cannot-extend-cidr.runConfigurationExtension
        bundledPlugins("com.intellij.clion", "com.intellij.cidr.base", "com.intellij.cidr.lang", "com.intellij.nativeDebug")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.junit.four)
}
