import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val intellijUltimateBuildVersion: String by project
val ultimateHome: String? = System.getenv("ULTIMATE_HOME")

// The environment variables ARTIFACTORY_PRIVATE_USERNAME and ARTIFACTORY_PRIVATE_PASSWORD are used on CI env
// On local box, please add artifactoryUsername and artifactoryPassword to ~/.gradle/gradle.properties
val artifactoryUsername = System.getenv("ARTIFACTORY_PRIVATE_USERNAME")
    ?: (if (project.hasProperty("artifactoryUsername")) project.property("artifactoryUsername").toString() else "")
val artifactoryPassword = System.getenv("ARTIFACTORY_PRIVATE_PASSWORD")
    ?: (if (project.hasProperty("artifactoryPassword")) project.property("artifactoryPassword").toString() else "")

plugins {
    id("org.jetbrains.intellij.platform.module")
    kotlin("jvm")
    java
    idea
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.license)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        apiVersion = "1.7"
        jvmTarget = "17"
    }
}

configurations.archives.get().isCanBeResolved = true

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
            // avoid dependency confusion
            excludeGroupByRegex("com\\.sonarsource.*")
        }
    }
    intellijPlatform {
        defaultRepositories()
        localPlatformArtifacts()
    }
}

tasks.cyclonedxBom {
    setIncludeConfigs(listOf("runtimeClasspath", "sqplugins_deps"))
    inputs.files(configurations.runtimeClasspath, configurations.archives.get())
    mustRunAfter(
        getTasksByName("buildPluginBlockmap", true)
    )
}

val bomFile = layout.buildDirectory.file("reports/bom.json")
artifacts.add("archives", bomFile.get().asFile) {
    name = "sonarlint-intellij"
    type = "json"
    classifier = "cyclonedx"
    builtBy("cyclonedxBom")
}

license {
    header = rootProject.file("HEADER")
    mapping(
        mapOf(
            "java" to "SLASHSTAR_STYLE",
            "kt" to "SLASHSTAR_STYLE",
            "svg" to "XML_STYLE",
            "form" to "XML_STYLE"
        )
    )
    excludes(
        listOf("**/*.jar", "**/*.png", "**/README")
    )
    strictCheck = true
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

tasks {
    // Make initializeIntellijPlatformPlugin task cacheable
    named("initializeIntellijPlatformPlugin") {
        outputs.cacheIf { true }
        outputs.upToDateWhen { true }

        // Add explicit input/output declarations to help with caching
        inputs.property("intellijPlatformVersion", intellijUltimateBuildVersion)
        inputs.property("ultimateHome", ultimateHome ?: "")
        outputs.dir(layout.buildDirectory.dir("tmp/initializeIntelliJPlugin"))
    }
}
