import org.jetbrains.intellij.platform.gradle.TestFrameworkType

val clionBuildVersion: String by project
val clionHome: String? = System.getenv("CLION_HOME")

plugins {
    id("org.jetbrains.intellij.platform.module")
}

dependencies {
    intellijPlatform {
        if (!clionHome.isNullOrBlank()) {
            println("Using local installation of CLion: $clionHome")
            local(clionHome)
        } else {
            println("No local installation of CLion found, using version $clionBuildVersion")
            clion(clionBuildVersion)
        }
        bundledPlugins("com.intellij.clion", "com.intellij.cidr.base", "com.intellij.cidr.lang")
        testFramework(TestFrameworkType.JUnit5)
    }
    implementation(project(":common"))
    implementation(project(":clion-common"))
    testRuntimeOnly(libs.junit.four)
    testImplementation(libs.junit.api)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.engine)
}

tasks.test {
    useJUnitPlatform()
}
