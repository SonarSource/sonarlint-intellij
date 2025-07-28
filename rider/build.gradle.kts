val riderBuildVersion: String by project
val riderHome: String? = System.getenv("RIDER_HOME")

plugins {
    alias(libs.plugins.intellij)
    java
    idea
    kotlin("jvm")
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
        if (!riderHome.isNullOrBlank()) {
            println("Using local installation of Rider: $riderHome")
            local(riderHome)
        } else {
            println("No local installation of Rider found, using version $riderBuildVersion")
            rider(riderBuildVersion, useInstaller = false)
        }
        bundledPlugins("Git4Idea")
    }
    implementation(project(":common"))
    implementation(project(":git"))
    compileOnly(libs.findbugs.jsr305)
}
