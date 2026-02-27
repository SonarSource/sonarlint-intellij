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
val runIdeDirectory: String by project
group = "org.sonarsource.sonarlint.intellij.its"
description = "ITs for SonarLint IntelliJ"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

intellijPlatform {
    projectName = "sonarlint-intellij"
    buildSearchableOptions = false
}

dependencies {
    intellijPlatform {
        if (project.hasProperty("ijVersion")) {
            val type = ijVersion.split('-')[0]
            val version = ijVersion.split('-')[1]

            // First check if setup-qa-ide.sh set an environment variable
            val envVarPath = when (type) {
                "IC", "IU" -> System.getenv("IDEA_HOME")
                "CL" -> System.getenv("CLION_HOME")
                "RD" -> System.getenv("RIDER_HOME")
                "PY", "PC" -> System.getenv("PYCHARM_HOME")
                "PS" -> System.getenv("PHPSTORM_HOME")
                "GO" -> System.getenv("GOLAND_HOME")
                else -> null
            }

            if (envVarPath != null && File(envVarPath).exists()) {
                println("ITs: Using IDE from setup-qa-ide.sh: $envVarPath (ijVersion=$ijVersion)")
                local(envVarPath)
            } else {
                val isCI = System.getenv("CI") == "true"
                if (isCI) {
                    // On CI: FAIL - setup-qa-ide.sh should have provided the IDE
                    throw GradleException("""
                        |IDE not provided for ITs on CI (ijVersion=$ijVersion)
                        |Expected environment variable: ${when(type) {
                            "IC", "IU" -> "IDEA_HOME"
                            "CL" -> "CLION_HOME"
                            "RD" -> "RIDER_HOME"
                            "PY", "PC" -> "PYCHARM_HOME"
                            "PS" -> "PHPSTORM_HOME"
                            "GO" -> "GOLAND_HOME"
                            else -> "<IDE>_HOME"
                        }}
                        |
                        |This means setup-qa-ide.sh did not run successfully or did not set the environment variable.
                        |Check the 'Setup IDE' step in the workflow logs.
                    """.trimMargin())
                } else {
                    // Local development: fall back to downloading from Repox
                    println("ITs: WARNING: No *_HOME env var set, downloading $ijVersion from Repox (local development only)")
                    create(type, version) {
                        useCache = true
                    }
                }
            }
        } else {
            intellijIdeaCommunity(intellijBuildVersion)
        }
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Bundled)
    }
    testImplementation(libs.its.orchestrator) {
        exclude(group = "org.slf4j", module = "log4j-over-slf4j")
    }
    testImplementation(libs.its.sonar.scala)
    testImplementation(libs.its.sonar.ws)
    testImplementation(libs.bundles.its.remote)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.junit.four)
    testRuntimeOnly(libs.junit.launcher)
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

tasks.named<Test>("test") {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    )
}

val runIdeForUiTests by intellijPlatformTesting.runIde.registering {
    if (project.hasProperty("runIdeDirectory")) {
        localPath = file(runIdeDirectory)
    }

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
                "-Dide.experimental.ui.navbar.scroll=true",
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
            sinceBuild = "231.9423.4"
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
