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

val ideCacheDir: String? = System.getenv("IDE_CACHE_DIR")
val cachedIntellijVersion: String? = System.getenv("INTELLIJ_VERSION")
val cachedClionVersion: String? = System.getenv("CLION_VERSION")
val cachedRiderVersion: String? = System.getenv("RIDER_VERSION")
val cachedResharperVersion: String? = System.getenv("RESHARPER_VERSION")
val cachedUltimateVersion: String? = System.getenv("ULTIMATE_VERSION")

/**
 * Check if the IDE is available via container environment variables.
 * Returns the path from container env vars like IDEA_2023_DIR, IDEA_2024_DIR, etc.
 */
fun getContainerIdePath(type: String, version: String): String? {
    // Extract year from version (e.g., "2023.3.8" -> "2023")
    val year = version.split('.').firstOrNull()

    return when (type) {
        "IC" -> System.getenv("IDEA_${year}_DIR")
        "IU" -> System.getenv("IDEA_ULTIMATE_${year}_DIR")
        "CL" -> System.getenv("CLION_${year}_DIR")
        "RD" -> System.getenv("RIDER_${year}_DIR")
        "PY" -> when (year) {
            "2023", "2024" -> System.getenv("PYCHARM_PRO_${year}_DIR")
            else -> System.getenv("PYCHARM_${year}_DIR")
        }
        "PC" -> when (year) {
            "2023", "2024" -> System.getenv("PYCHARM_COM_${year}_DIR")
            else -> System.getenv("PYCHARM_${year}_DIR")  // 2025+ uses unified path
        }
        "PS" -> System.getenv("PHPSTORM_${year}_DIR")
        "GO" -> System.getenv("GOLAND_${year}_DIR")
        else -> null
    }
}

/**
 * Check if the given ijVersion (e.g., "IC-2023.1.7") matches one of the cached IDEs.
 * Returns the local path if cached, null otherwise.
 * Checks container environment variables first, then falls back to legacy cache dir.
 */
fun getCachedIdePath(ijVersion: String?): String? {
    if (ijVersion.isNullOrBlank()) return null

    val parts = ijVersion.split('-')
    if (parts.size != 2) return null

    val type = parts[0]
    val version = parts[1]

    // First, check container environment variables (for container-based jobs)
    val containerPath = getContainerIdePath(type, version)
    if (containerPath != null && File(containerPath).exists()) {
        return containerPath
    }

    // Fallback to legacy cache dir approach
    return when {
        type == "IC" && version == cachedIntellijVersion -> "$ideCacheDir/intellij"
        type == "IU" && version == cachedUltimateVersion -> "$ideCacheDir/ultimate"
        type == "CL" && version == cachedClionVersion -> "$ideCacheDir/clion"
        type == "CL" && version == cachedResharperVersion -> "$ideCacheDir/resharper"
        type == "RD" && version == cachedRiderVersion -> "$ideCacheDir/rider"
        else -> null
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
                    // Local development: allow fallback to download
                    println("ITs: WARNING: Environment variable not set, using legacy resolution (local development)")
                    val cachedPath = getCachedIdePath(ijVersion)

                    if (cachedPath != null && File(cachedPath).exists()) {
                        println("ITs: Using cached IDE from container or cache: $cachedPath (ijVersion=$ijVersion)")
                        local(cachedPath)
                    } else {
                        println("ITs: Downloading IDE $ijVersion from repository (local development only)")
                        create(type, version) {
                            useCache = true
                        }
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
