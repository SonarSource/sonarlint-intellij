import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.intellij.platform")
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.license)
    alias(libs.plugins.kotlin)
    jacoco
}

apply(from = "${rootProject.projectDir}/gradle/module-conventions.gradle")

// ITs require IDEs whose test-framework JARs introduce transitive deps that vary by environment
dependencyLocking {
    lockMode.set(LockMode.LENIENT)
}

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

// IT coverage is opt-in: enable with -PitCoverage=true.
// When enabled, the JaCoCo agent is attached to the IDE JVM in TCP-server mode so we
// can dump coverage on demand (the IDE JVM is usually killed, not shut down cleanly).
val isItCoverageEnabled = project.hasProperty("itCoverage")
val itCoveragePort = (project.findProperty("itCoveragePort") as? String)?.toInt() ?: 6300
val ideCoverageExecFile = layout.buildDirectory.file("jacoco/runIdeForUiTests.exec")

// We need the standalone jacocoagent.jar (the `runtime` classifier of `org.jacoco.agent`) so we
// can pass it to the IDE JVM as a -javaagent. The `jacoco` plugin's built-in `jacocoAgent`
// configuration resolves to a ZIP containing jacocoagent.jar, which is less convenient; a
// dedicated configuration with the `runtime` classifier gives us the JAR directly.
val itJacocoAgent: Configuration = configurations.create("itJacocoAgent") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    "itJacocoAgent"("org.jacoco:org.jacoco.agent:${jacoco.toolVersion}:runtime@jar")
}

val runIdeForUiTests by intellijPlatformTesting.runIde.registering {
    if (project.hasProperty("runIdeDirectory")) {
        localPath = file(runIdeDirectory)
    }

    task {
        jvmArgumentProviders += CommandLineArgumentProvider {
            val baseArgs = listOf(
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
            if (isItCoverageEnabled) {
                val agentJar = itJacocoAgent.singleFile.absolutePath
                baseArgs + "-javaagent:$agentJar=output=tcpserver,address=*,port=$itCoveragePort,includes=org.sonarlint.intellij.*,append=false"
            } else baseArgs
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

    prepareSandboxTask {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

// Dumps JaCoCo coverage from the running IDE's TCP server into a local .exec file.
// Wired as a finalizer on :its:test (only when -PitCoverage is set) so coverage is captured
// even when tests fail.
val dumpIdeCoverage by tasks.registering {
    group = "verification"
    description = "Dumps JaCoCo coverage from the running IDE's TCP server."
    val outFile = ideCoverageExecFile.get().asFile
    val port = itCoveragePort
    val jacocoAntClasspath = configurations.named("jacocoAnt")
    outputs.file(outFile)
    onlyIf { isItCoverageEnabled }

    doLast {
        outFile.parentFile.mkdirs()
        try {
            ant.withGroovyBuilder {
                "taskdef"(
                    "name" to "jacocoDump",
                    "classname" to "org.jacoco.ant.DumpTask",
                    "classpath" to jacocoAntClasspath.get().asPath
                )
                "jacocoDump"(
                    "address" to "localhost",
                    "port" to port,
                    "reset" to false,
                    "destfile" to outFile.absolutePath,
                    "append" to false
                )
            }
            logger.lifecycle("IT coverage dumped to ${outFile.absolutePath}")
        } catch (e: Exception) {
            // Don't fail the build if the IDE isn't reachable - tests may have aborted before the IDE started.
            logger.warn("Failed to dump IT coverage from localhost:$port: ${e.message}")
        }
    }
}

// Generates a JaCoCo report for SonarLint plugin code exercised by the ITs.
// Picks up every *.exec file under build/jacoco/ so it can also be used to merge
// coverage collected from several matrix jobs (CI downloads them side-by-side).
tasks.register<JacocoReport>("jacocoIdeReport") {
    group = "verification"
    description = "Generates a JaCoCo coverage report for SonarLint plugin code exercised by the ITs."

    executionData.setFrom(
        fileTree(layout.buildDirectory.dir("jacoco")) {
            include("**/*.exec")
        }
    )

    // The composed plugin includes the root project plus every `pluginComposedModule` sub-module
    // listed in the root build.gradle.kts. The IDE JVM loads all of them, so the report needs
    // their sources and instrumented classes too.
    val pluginModulePaths = listOf("", "common", "clion", "clion-resharper", "nodejs", "rider", "git")
    val moduleProjects = pluginModulePaths.map { if (it.isEmpty()) rootProject else rootProject.project(":$it") }
    sourceDirectories.setFrom(
        moduleProjects.flatMap { p ->
            listOf(p.file("src/main/java"), p.file("src/main/kotlin"))
        }.filter { it.exists() }
    )
    // Same instrumented output the unit-test report uses, so forms/etc. are included.
    classDirectories.setFrom(
        moduleProjects.map { it.layout.buildDirectory.dir("instrumented/instrumentCode") }
    )

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    onlyIf {
        executionData.files.any { it.exists() && it.length() > 0 }
    }
}

tasks.named<Test>("test") {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    )
    // The test JVM only drives the IDE remotely - it never loads plugin code - so the default
    // JaCoCo agent attached by the `jacoco` plugin would produce an empty exec file. Disable it
    // to keep build/jacoco/ clean for the IDE JVM dump.
    configure<JacocoTaskExtension> {
        isEnabled = false
    }
    if (isItCoverageEnabled) {
        finalizedBy(dumpIdeCoverage)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242.20224.300"
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
