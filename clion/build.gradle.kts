import org.jetbrains.intellij.platform.gradle.TestFrameworkType

val clionBuildVersion: String by project
val clionHome: String? = System.getenv("CLION_HOME")

plugins {
    alias(libs.plugins.intellij)
    java
    idea
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.license)
    alias(libs.plugins.kotlin)
}

apply(from = "${rootProject.projectDir}/gradle/module-conventions.gradle")

repositories {
    maven("https://repox.jfrog.io/repox/sonarsource") {
        credentials {
            username = System.getenv("ARTIFACTORY_PRIVATE_USERNAME") ?: ""
            password = System.getenv("ARTIFACTORY_PRIVATE_PASSWORD") ?: ""
        }
    }
    mavenCentral {
        content {
            excludeGroupByRegex("com\\.sonarsource.*")
        }
    }
    intellijPlatform {
        defaultRepositories()
    }
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
    implementation(project(":clion-common"))
    testImplementation(libs.junit.api)
    testImplementation(libs.mockito.core)
    testImplementation(libs.junit.four)
    testImplementation(libs.junit.engine)
}
