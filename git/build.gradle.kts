val intellijBuildVersion: String by project
val ideaHome: String? = System.getenv("IDEA_HOME")

plugins {
    alias(libs.plugins.intellij)
    idea
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.license)
    kotlin("jvm")
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
        if (!ideaHome.isNullOrBlank()) {
            local(ideaHome)
        } else {
            intellijIdeaCommunity(intellijBuildVersion)
        }
        bundledPlugins("Git4Idea")
    }
    implementation(project(":common"))
}
