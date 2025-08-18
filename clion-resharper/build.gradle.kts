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
        if (!clionHome.isNullOrBlank()) {
            println("Using local installation of CLion: $clionHome")
            local(clionHome)
        } else {
            println("No local installation of CLion found, using version $clionBuildVersion")
            clion(clionBuildVersion)
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
    compileOnly(libs.findbugs.jsr305)
}
