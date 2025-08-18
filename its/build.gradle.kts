import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.intellij.platform")
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.license)
    alias(libs.plugins.kotlin)
}

apply(from = "${rootProject.projectDir}/gradle/module-conventions.gradle")

val intellijBuildVersion: String by project
val ijVersion: String by project
group = "org.sonarsource.sonarlint.intellij.its"
description = "ITs for SonarLint IntelliJ"

intellijPlatform {
    projectName = "sonarlint-intellij"
    buildSearchableOptions = false
}

dependencies {
    intellijPlatform {
        if (project.hasProperty("ijVersion")) {
            val type = ijVersion.split('-')[0]
            val version = ijVersion.split('-')[1]
            create(type, version)
        } else {
            intellijIdeaCommunity(intellijBuildVersion)
        }
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("org.sonarsource.orchestrator:sonar-orchestrator-junit5:5.1.0.2254") {
        exclude(group = "org.slf4j", module = "log4j-over-slf4j")
    }
    testImplementation(libs.its.sonar.scala)
    testImplementation(libs.its.sonar.ws)
    testImplementation(libs.bundles.its.remote)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.junit.four)
}

tasks {
    compileKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    compileTestKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    test {
        useJUnitPlatform {
            val tag = System.getenv("TEST_SUITE")
            if (tag != null && (tag == "OpenInIdeTests" || tag == "ConnectedAnalysisTests"
                    || tag == "ConfigurationTests" || tag == "Standalone")) {
                includeTags(tag)
            }
        }
        testLogging.showStandardStreams = true
    }
}

val runIdeForUiTests by intellijPlatformTesting.runIde.registering {
    task {
        jvmArgumentProviders += CommandLineArgumentProvider {
            listOf(
                "-Xmx1G",
                "-Drobot-server.port=8082",
                "-Drobot-server.host.public=true",
                "-Dsonarlint.internal.sonarcloud.url=https://sc-staging.io",
                "-Dsonarlint.internal.sonarcloud.api.url=https://api.sc-staging.io",
                "-Dsonarlint.internal.sonarcloud.websocket.url=wss://events-api.sc-staging.io/",
                "-Dsonarlint.internal.sonarcloud.us.url=https://us-sc-staging.io",
                "-Dsonarlint.internal.sonarcloud.us.api.url=https://api.us-sc-staging.io",
                "-Dsonarlint.internal.sonarcloud.us.websocket.url=wss://events-api.us-sc-staging.io",
                "-Dsonarlint.telemetry.disabled=true",
                "-Dsonarlint.monitoring.disabled=true",
                "-Dsonarlint.logs.verbose=true",
                "-Didea.trust.all.projects=true",
                "-Dide.show.tips.on.startup.default.value=false",
                "-Djb.privacy.policy.text=<!--999.999-->",
                "-Djb.consents.confirmation.enabled=false",
                "-Deap.require.license=true"
            )
        }

        doFirst {
            if (project.hasProperty("slPluginDirectory")) {
                copy {
                    from(project.property("slPluginDirectory"))
                    into(sandboxPluginsDirectory)
                }
            }
        }
    }

    plugins {
        robotServerPlugin("0.11.23")
        // Only depend on building the root project plugin if slPluginDirectory is not provided
        if (!project.hasProperty("slPluginDirectory")) {
            localPlugin(rootProject.dependencies.project(":"))
        }
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
        }
        name = "sonarlint-intellij-its"
    }
    instrumentCode.set(false)
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
    excludes(listOf("**/*.jar", "**/*.png", "**/README", "**.xml"))
    strictCheck = true
}
