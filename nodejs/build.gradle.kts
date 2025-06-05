val intellijUltimateBuildVersion: String by project
val ultimateHome: String? = System.getenv("ULTIMATE_HOME")

plugins {
    id("org.jetbrains.intellij.platform.module")
    kotlin("jvm")
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
        if (!ultimateHome.isNullOrBlank()) {
            println("Using local installation of Ultimate: $ultimateHome")
            local(ultimateHome)
        } else {
            println("No local installation of Ultimate found, using version $intellijUltimateBuildVersion")
            intellijIdeaUltimate(intellijUltimateBuildVersion)
        }
        bundledPlugins("JavaScript")
    }
    implementation(project(":common"))
}
