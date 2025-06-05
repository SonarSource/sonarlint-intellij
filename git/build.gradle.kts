val intellijBuildVersion: String by project
val ideaHome: String? = System.getenv("IDEA_HOME")

plugins {
    id("org.jetbrains.intellij.platform.module")
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
    testImplementation(project(":test-common"))
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}

tasks.test {
    useJUnitPlatform()
}
