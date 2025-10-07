import org.jetbrains.intellij.platform.gradle.TestFrameworkType

val resharperBuildVersion: String by project
val resharperHome: String? = System.getenv("RESHARPER_HOME")

plugins {
    id("org.jetbrains.intellij.platform.module")
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.license)
    alias(libs.plugins.kotlin)
}

apply(from = "${rootProject.projectDir}/gradle/module-conventions.gradle")

dependencies {
    intellijPlatform {
        if (!resharperHome.isNullOrBlank()) {
            println("Using local installation of CLion: $resharperHome")
            local(resharperHome)
        } else {
            println("No local installation of CLion found, using version $resharperBuildVersion")
            clion(resharperBuildVersion)
        }
        pluginComposedModule(implementation(project(":common")))
        pluginComposedModule(implementation(project(":clion")))
        // nativeDebug: https://youtrack.jetbrains.com/issue/CPP-43231/Cannot-extend-cidr.runConfigurationExtension
        bundledPlugins("org.jetbrains.plugins.clion.radler", "com.intellij.clion", "com.intellij.cidr.base", "com.intellij.cidr.lang", "com.intellij.nativeDebug")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation(libs.mockito.core)
    testImplementation(libs.junit.four)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    compileOnly(libs.findbugs.jsr305)
}
