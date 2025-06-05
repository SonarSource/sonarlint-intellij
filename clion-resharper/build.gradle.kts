import org.jetbrains.intellij.platform.gradle.TestFrameworkType

val clionResharperBuildVersion: String by project
val resharperHome: String? = System.getenv("RESHARPER_HOME")

plugins {
    id("org.jetbrains.intellij.platform.module")
    java
    idea
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.license)
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
        if (!resharperHome.isNullOrBlank()) {
            println("Using local installation of Rider: $resharperHome")
            local(resharperHome)
        } else {
            println("No local installation of Rider found, using version $clionResharperBuildVersion")
            rider(clionResharperBuildVersion, useInstaller = false)
        }
        testFramework(TestFrameworkType.Platform)
    }
    implementation(project(":clion-common"))
    testImplementation(libs.junit.api)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.engine)
    compileOnly(libs.findbugs.jsr305)
}
