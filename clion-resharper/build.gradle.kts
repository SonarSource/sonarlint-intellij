import org.jetbrains.intellij.platform.gradle.TestFrameworkType

val clionResharperBuildVersion: String by project
val resharperHome: String? = System.getenv("RESHARPER_HOME")

plugins {
    alias(libs.plugins.intellij)
    java
    idea
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.license)
}

apply(from = "${rootProject.projectDir}/gradle/module-conventions.gradle")

// The environment variables ARTIFACTORY_PRIVATE_USERNAME and ARTIFACTORY_PRIVATE_PASSWORD are used on CI env
// On local box, please add artifactoryUsername and artifactoryPassword to ~/.gradle/gradle.properties
val artifactoryUsername = System.getenv("ARTIFACTORY_PRIVATE_USERNAME")
    ?: (if (project.hasProperty("artifactoryUsername")) project.property("artifactoryUsername").toString() else "")
val artifactoryPassword = System.getenv("ARTIFACTORY_PRIVATE_PASSWORD")
    ?: (if (project.hasProperty("artifactoryPassword")) project.property("artifactoryPassword").toString() else "")

repositories {
    maven("https://repox.jfrog.io/repox/sonarsource") {
        if (artifactoryUsername.isNotEmpty() && artifactoryPassword.isNotEmpty()) {
            credentials {
                username = artifactoryUsername
                password = artifactoryPassword
            }
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
    testImplementation(libs.mockito.core)
    testImplementation(libs.junit.four)
    testImplementation(libs.junit.jupiter)
    compileOnly(libs.findbugs.jsr305)
}
