
import com.jetbrains.plugin.blockmap.core.BlockMap
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

tasks.matching { it.name == "signArchives" }.configureEach {
    dependsOn(":composedJar")
}

plugins {
    java
    alias(libs.plugins.kotlin)
    id("org.jetbrains.intellij.platform")
    alias(libs.plugins.sonarqube)
    jacoco
    alias(libs.plugins.spotless)
    alias(libs.plugins.artifactory)
    idea
    signing
    alias(libs.plugins.cyclonedx)
}

buildscript {
    dependencies {
        "classpath"("org.jetbrains.intellij:blockmap:1.0.10")
    }
}

// The SonarQube CLI executable for the build machine, e.g. "macos-arm64@bin". There is no macOS Intel build: those Macs
// still resolve the arm64 one, so the lock state is the same everywhere, but cannot run it.
val buildMachineIsArm = System.getProperty("os.arch") in setOf("aarch64", "arm64")
val buildMachineOs = System.getProperty("os.name").lowercase()
val sonarQubeCliPlatform = when {
    buildMachineOs.contains("win") -> "windows-x86-64@exe"
    buildMachineOs.contains("mac") -> "macos-arm64@bin"
    buildMachineIsArm -> "linux-arm64@bin"
    else -> "linux-x86-64@bin"
}
val sonarQubeCliRunsOnBuildMachine = !(buildMachineOs.contains("mac") && !buildMachineIsArm)

group = "org.sonarsource.sonarlint.intellij"
description = "SonarLint for IntelliJ IDEA"

val intellijBuildType: String by project
val intellijBuildVersion: String by project
val ideaHome: String? = System.getenv("IDEA_HOME")
val runIdeDirectory: String by project

// The environment variables ARTIFACTORY_USERNAME and ARTIFACTORY_ACCESS_TOKEN are used on CI
// On local box, please add artifactoryUrl, artifactoryUsername and artifactoryPassword to ~/.gradle/gradle.properties
val artifactoryUrl = System.getenv("ARTIFACTORY_URL")
	?: (if (project.hasProperty("artifactoryUrl")) project.property("artifactoryUrl").toString() else "")
val artifactoryUsername = System.getenv("ARTIFACTORY_USERNAME")
    ?: System.getenv("ARTIFACTORY_ACCESS_USERNAME")
    ?: (if (project.hasProperty("artifactoryUsername")) project.property("artifactoryUsername").toString() else "")
val artifactoryPassword = System.getenv("ARTIFACTORY_ACCESS_TOKEN")
    ?: System.getenv("ARTIFACTORY_PASSWORD")
    ?: (if (project.hasProperty("artifactoryPassword")) project.property("artifactoryPassword").toString() else "")

configurations {
    val sqplugins = create("sqplugins") { isTransitive = false }
    register("sqplugins_deps") {
        extendsFrom(sqplugins)
        isTransitive = true
    }
    register("sloop")
    register("sonarqubeCli")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        if (!ideaHome.isNullOrBlank()) {
            local(ideaHome)
        } else {
            create(intellijBuildType, intellijBuildVersion)
        }
        pluginComposedModule(implementation(project(":common")))
        pluginComposedModule(runtimeOnly(project(":clion")))
        pluginComposedModule(runtimeOnly(project(":clion-resharper")))
        pluginComposedModule(runtimeOnly(project(":nodejs")))
        pluginComposedModule(runtimeOnly(project(":rider")))
        pluginComposedModule(runtimeOnly(project(":git")))
        bundledPlugins("com.intellij.java", "Git4Idea", "org.jetbrains.plugins.terminal")
        testFramework(TestFrameworkType.Platform)
        pluginVerifier(libs.versions.plugin.verifier.get())
    }
    implementation(libs.sonarlint.java.client.utils)
    implementation(libs.sonarlint.rpc.java.client)
    implementation(libs.sonarlint.rpc.impl)
    implementation(libs.commons.langs3)
    implementation(libs.commons.text)
    implementation(libs.sentry)
    compileOnly(libs.findbugs.jsr305)
    testImplementation(libs.junit.four)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.awaitility)
    testImplementation(testFixtures(project("test-common")))
    testRuntimeOnly(libs.junit.launcher)
    "sqplugins"(libs.bundles.sonar.analyzers)
    "sloop"("org.sonarsource.sonarlint.core:sonarlint-backend-cli:${libs.versions.sonarlint.core.get()}:no-arch@zip")
    "sonarqubeCli"("org.sonarsource.cli:sonarqube-cli:${libs.versions.sonarqube.cli.get()}:$sonarQubeCliPlatform")
}

apply(from = "gradle/intellij-dependency-lock.gradle")

apply(from = "gradle/spotless.gradle")

intellijPlatform {
    caching {
        ides {
            enabled = true
        }
    }
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242.20224.300"
            untilBuild = provider { null }
        }
    }
    buildSearchableOptions = true
    pluginVerification {
        // Keep internal API usages visible in reports without failing verification.
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.NON_EXTENDABLE_API_USAGES,
            VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
            VerifyPluginTask.FailureLevel.PLUGIN_STRUCTURE_WARNINGS,
            VerifyPluginTask.FailureLevel.INVALID_PLUGIN
        )
        
        val verifyFullMatrix = providers.gradleProperty("verifierEnv").orNull == "FULL"
        ignoredProblemsFile = rootProject.file(".verifier-ignored-problems.txt")

        ides {
            val minimumVersion = providers.gradleProperty("minSupportedIdeVersion").get()
            create(IntelliJPlatformType.IntellijIdeaCommunity, minimumVersion)
            if (verifyFullMatrix) {
                create(IntelliJPlatformType.IntellijIdeaUltimate, minimumVersion)
                create(IntelliJPlatformType.Rider, minimumVersion)
                create(IntelliJPlatformType.CLion, minimumVersion)
                create(IntelliJPlatformType.WebStorm, minimumVersion)
                val ideTypes = listOf(
                    IntelliJPlatformType.IntellijIdea, IntelliJPlatformType.PyCharm, IntelliJPlatformType.AndroidStudio,
                    IntelliJPlatformType.RubyMine, IntelliJPlatformType.GoLand, IntelliJPlatformType.PhpStorm,
                    IntelliJPlatformType.Rider, IntelliJPlatformType.CLion, IntelliJPlatformType.WebStorm
                )
                // Select each channel separately so EAPs do not replace stable targets.
                for (channel in listOf(ProductRelease.Channel.RELEASE, ProductRelease.Channel.EAP)) {
                    latest {
                        types = ideTypes
                        channels = listOf(channel)
                    }
                }
            }
        }
    }

    sonar {
        properties {
            property("sonar.projectName", "SonarLint for IntelliJ IDEA")
            property("sonar.sca.exclusions", "its/projects/**")
            // Fix deprecated implicit compilation
            property("sonar.gradle.skipCompile", "true")
            property("sonar.organization", "sonarsource")
        }
    }

    artifactory {
        buildInfo {
            buildName = "sonarlint-intellij"
            buildNumber = System.getenv("BUILD_NUMBER") ?: ""
            addEnvironmentProperty(
                "ARTIFACTS_TO_DOWNLOAD",
                "org.sonarsource.sonarlint.intellij:sonarlint-intellij:zip,org.sonarsource.sonarlint.intellij:sonarlint-intellij:json:cyclonedx"
            )
        }
        clientConfig.isIncludeEnvVars = true
        clientConfig.envVarsExcludePatterns =
            "*password*,*PASSWORD*,*secret*,*MAVEN_CMD_LINE_ARGS*,sun.java.command,*token*,*TOKEN*,*LOGIN*,*login*,*key*,*KEY*,*PASSPHRASE*,*signing*"
        // Publish always targets SaaS. CI may point ARTIFACTORY_URL at Edge for resolve.
        setContextUrl(
            System.getenv("ARTIFACTORY_PUBLISH_URL") ?: "https://repox.jfrog.io/artifactory"
        )
        publish {
            repository {
                repoKey = System.getenv("ARTIFACTORY_DEPLOY_REPO") ?: ""
                username = System.getenv("ARTIFACTORY_DEPLOY_USERNAME") ?: ""
                password = System.getenv("ARTIFACTORY_DEPLOY_ACCESS_TOKEN") ?: ""
            }
            defaults {
                setProperties(
                    mapOf(
                        "vcs.revision" to (System.getenv("GITHUB_SHA") ?: ""),
                        "vcs.branch" to (System.getenv("GITHUB_HEAD_REF")
                            ?: System.getenv("GITHUB_REF_NAME") ?: ""),
                        "build.name" to "sonarlint-intellij",
                        "build.number" to (System.getenv("BUILD_NUMBER") ?: "")
                    )
                )
                this.addDefaultArchiveConfigurations()
                setPublishPom(false)
                setPublishIvy(false)
            }
        }
    }
}

tasks.named<VerifyPluginTask>("verifyPlugin") {
    doLast {
        if (listIdes.get()) return@doLast
        // Optional Java exclusions must never apply to IDEs that provide Java.
        verificationReportsDirectory.get().asFile.listFiles().orEmpty()
            .filter { ideReport -> listOf("IC-", "IU-", "AI-").any(ideReport.name::startsWith) }
            .forEach { ideReport ->
                // Reports also contain automatically ignored, documented API changes.
                // Only reject the explicit Java exclusions from ignoredProblemsFile.
                check(ideReport.walkTopDown().none { report ->
                    report.name == "ignored-problems.txt" && report.useLines { lines ->
                        lines.any { it.startsWith("because the problem is ignored by RegExp pattern:") }
                    }
                }) {
                    "Optional Java API problems were excluded in ${ideReport.name}"
                }
            }
    }
}

intellijPlatformTesting {
    runIde {
        register("runLocalIde") {
            if (project.hasProperty("runIdeDirectory")) {
                localPath = file(runIdeDirectory)
            }
            sandboxDirectory = project.layout.buildDirectory.dir("sonarlint-test")
            prepareSandboxTask {
                doLast {
                    setupSandbox(destinationDir, pluginName)
                }
            }
        }
    }
}

fun setupSandbox(destinationDir: File, pluginName: Property<String>) {
    val pluginsDir = file("$destinationDir/${pluginName.get()}/plugins")

    copyPlugins(pluginsDir)
    copySloop(destinationDir, pluginName)
    unzipEslintBridgeBundle(pluginsDir)
}

fun copyPlugins(pluginsDir: File) {
    copy {
        from(project.configurations["sqplugins"])
        into(pluginsDir)
    }
}

fun copySloop(destinationDir: File, pluginName: Property<String>) {
    configurations["sloop"].resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
        copy {
            from(zipTree(artifact.file))
            into(file("$destinationDir/${pluginName.get()}/sloop/"))
        }
    }
}

fun unzipEslintBridgeBundle(pluginsDir: File) {
    val jarPath = pluginsDir.listFiles()?.find {
        it.name.startsWith("sonar-javascript-plugin-") && it.name.endsWith(".jar")
    } ?: throw GradleException("sonar-javascript-plugin-* JAR not found in $pluginsDir")

    ZipFile(jarPath).use { zipFile ->
        val entry = zipFile.entries().asSequence().find { it.name.matches(Regex("sonarjs-.*\\.tgz")) }
            ?: throw GradleException("eslint bridge server bundle not found in JAR $jarPath")

        val outputFolderPath = Paths.get("$pluginsDir/eslint-bridge")
        val outputFilePath = outputFolderPath.resolve(entry.name)

        Files.createDirectories(outputFolderPath)

        zipFile.getInputStream(entry).use { input ->
            Files.copy(input, outputFilePath)
        }

        GzipCompressorInputStream(Files.newInputStream(outputFilePath)).use { gzipInput ->
            TarArchiveInputStream(gzipInput).use { tarInput ->
                generateSequence { tarInput.nextEntry }
                    .forEach { tarEntry ->
                        val outputFile = outputFolderPath.resolve(tarEntry.name).toFile()
                        if (tarEntry.isDirectory) {
                            outputFile.mkdirs()
                        } else {
                            outputFile.parentFile.mkdirs()
                            Files.copy(tarInput, outputFile.toPath())
                        }
                    }
            }
        }
        Files.deleteIfExists(outputFilePath)
    }
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

    jar {
        archiveClassifier = ""
    }

    runIde {
        systemProperty("sonarlint.telemetry.disabled", "true")
        systemProperty("sonarlint.monitoring.disabled", "true")
        // uncomment to customize the SonarCloud URL
        //systemProperty("sonarlint.internal.sonarcloud.url", "https://sonarcloud.io/")
    }

    test {
        useJUnitPlatform()
        javaLauncher.set(
            project.the<JavaToolchainService>().launcherFor {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        )
        systemProperty("sonarlint.telemetry.disabled", "true")
        systemProperty("sonarlint.monitoring.disabled", "true")
        // disable thread leak checker for now as it's raising FPs
        systemProperty("junit.jupiter.extensions.autodetection.exclude", "com.intellij.testFramework.junit5.impl.ThreadLeakTrackerExtension")
        // Hide the tools installed on the machine from the backend, so tests decide whether a CLI is installed: it gets its
        // own user home (and, on Windows, its own %LOCALAPPDATA%), where it still searches the official install locations,
        // and a search path replacing PATH (or the system paths on macOS): empty, or only the fake agent directory on Windows
        val sloopUserHome = layout.buildDirectory.dir("sloop-user-home").get().asFile
        // The backend splits SONARLINT_JVM_OPTS on spaces, so the user home can only be passed from a path without spaces.
        // Without it, the tests that need this isolation skip themselves.
        if (sloopUserHome.absolutePath.contains(' ')) {
            doFirst {
                logger.warn("Tests needing an isolated SonarQube for IDE backend are skipped: the build path contains spaces (${sloopUserHome.absolutePath})")
            }
        } else {
            systemProperty("sonarlint.test.sloop.user.home", sloopUserHome.absolutePath)
            val agentBinDirectory = sloopUserHome.resolve(".local/bin")
            val windows = buildMachineOs.contains("win")
            // On Windows the backend looks for the official CLI installation under %LOCALAPPDATA%, and for agent CLIs only
            // in the search path, so that path holds the fake agents there
            if (windows) {
                environment("LOCALAPPDATA", sloopUserHome.resolve("AppData/Local").absolutePath)
            }
            val searchPath = if (windows) agentBinDirectory.absolutePath else ""
            environment("SONARLINT_JVM_OPTS", listOfNotNull(System.getenv("SONARLINT_JVM_OPTS")?.takeIf { it.isNotBlank() },
                "-Duser.home=${sloopUserHome.absolutePath}", "-Dsonarlint.internal.ai.searchPath=$searchPath").joinToString(" "))
            // The real SonarQube CLI and `sonar integrate` work in the same user home as the backend, and keep their state and
            // token there instead of in the machine's keychain. Java keeps the IDE's own user.home. The CLI's home is HOME,
            // or USERPROFILE on Windows.
            environment("HOME", sloopUserHome.absolutePath)
            if (windows) {
                environment("USERPROFILE", sloopUserHome.absolutePath)
            }
            environment("SONAR_USER_HOME", sloopUserHome.resolve(".sonar").absolutePath)
            environment("SONARQUBE_CLI_KEYCHAIN_FILE", sloopUserHome.resolve("sonarqube-cli-keychain.json").absolutePath)
            // The real SonarQube CLI the AI Integrations tests install, resolved like the backend itself. It is only on
            // Artifactory: without it, e.g. on the Maven Central fallback, or on a machine it does not support, those tests
            // skip and the others still run.
            val sonarQubeCli = configurations["sonarqubeCli"].incoming.artifactView { lenient(true) }.files
            inputs.files(sonarQubeCli)
            jvmArgumentProviders += CommandLineArgumentProvider {
                sonarQubeCli.files.singleOrNull()?.takeIf { sonarQubeCliRunsOnBuildMachine }
                    ?.let { listOf("-Dsonarlint.test.sonarqube.cli=${it.absolutePath}") }.orEmpty()
            }
            doFirst {
                sloopUserHome.deleteRecursively()
                // The backend discovers agent CLIs once per process, at the first AI Integrations request of the run, so
                // the fake Claude Code used by AiIntegrationsTabLightTests must exist before any test runs
                agentBinDirectory.mkdirs()
                if (windows) {
                    agentBinDirectory.resolve("claude.cmd").writeText("@echo 2.0.0 (Claude Code)\r\n")
                } else {
                    agentBinDirectory.resolve("claude").apply {
                        writeText("#!/bin/sh\necho \"2.0.0 (Claude Code)\"\n")
                        setExecutable(true)
                    }
                }
            }
        }
    }

    prepareSandbox {
        doLast {
            setupSandbox(destinationDir, pluginName)
        }
    }

    prepareTestSandbox {
        doLast {
            setupSandbox(destinationDir, pluginName)
        }
    }

    cyclonedxDirectBom {
        includeConfigs = listOf("runtimeClasspath", "sqplugins_deps")
        xmlOutput.unsetConvention()
    }

    cyclonedxBom {
        jsonOutput.set(layout.buildDirectory.file("reports/bom.json"))
        xmlOutput.unsetConvention()
    }

    val buildPluginBlockmap by registering {
        group = "build"
        description = "Generates the blockmap and hash files for the plugin distribution."
        inputs.files(buildPlugin.flatMap { it.archiveFile })
            .withPropertyName("distribZip")
            .skipWhenEmpty()
        doLast {
            val distribZip = buildPlugin.get().archiveFile.get().asFile
            val blockMapBytes =
                com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(BlockMap(distribZip.inputStream()))
            val blockMapFile = File(distribZip.parentFile, "blockmap.json")
            blockMapFile.writeBytes(blockMapBytes)
            val blockMapFileZipFile = file(distribZip.absolutePath + ".blockmap.zip")
            val blockMapFileZip = ZipOutputStream(BufferedOutputStream(FileOutputStream(blockMapFileZipFile)))
            val fi = FileInputStream(blockMapFile)
            val origin = BufferedInputStream(fi)
            val entry = ZipEntry(blockMapFile.name)
            blockMapFileZip.putNextEntry(entry)
            origin.copyTo(blockMapFileZip, 1024)
            origin.close()
            blockMapFileZip.close()
            val fileHash = com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(com.jetbrains.plugin.blockmap.core.FileHash(distribZip.inputStream()))
            val fileHashJsonFile = file(distribZip.absolutePath + ".hash.json")
            fileHashJsonFile.writeText(fileHash)
        }
    }

    buildPlugin {
        finalizedBy(buildPluginBlockmap)
    }

    withType<Test> {
        configure<JacocoTaskExtension> {
            isIncludeNoLocationClasses = true
            excludes = listOf("jdk.internal.*")
        }
    }

    jacocoTestReport {
        classDirectories.setFrom(files("build/instrumented/instrumentCode"))
        reports {
            xml.required.set(true)
        }
    }

    artifactoryPublish {
        mustRunAfter(
            getTasksByName("cyclonedxBom", true),
            buildPlugin,
            getTasksByName("buildPluginBlockmap", true)
        )
    }
}

// Register all published artifacts in the 'archives' configuration at configuration time.
// Gradle 9 makes configurations immutable after they are consumed as a variant, so all
// artifact registrations must happen here (not in task doLast blocks).
artifacts {
    add("archives", tasks.named<Zip>("buildPlugin").flatMap { it.archiveFile }) {
        name = project.name
        extension = "zip"
        type = "zip"
    }
    add("archives", tasks.named<Zip>("buildPlugin").flatMap { it.archiveFile }.map {
        file(it.asFile.absolutePath + ".blockmap.zip")
    }) {
        name = project.name
        extension = "zip.blockmap.zip"
        type = "zip"
        builtBy(tasks.named("buildPluginBlockmap"))
    }
    add("archives", tasks.named<Zip>("buildPlugin").flatMap { it.archiveFile }.map {
        file(it.asFile.absolutePath + ".hash.json")
    }) {
        name = project.name
        extension = "zip.hash.json"
        type = "json"
        builtBy(tasks.named("buildPluginBlockmap"))
    }
    add("archives", layout.buildDirectory.file("reports/bom.json")) {
        name = "sonarlint-intellij"
        type = "json"
        classifier = "cyclonedx"
        builtBy(tasks.named("cyclonedxBom"))
    }
}

signing {
    setRequired {
        val branch = System.getenv("GITHUB_REF_NAME") ?: ""
        val pr = System.getenv("GITHUB_HEAD_REF") ?: ""
        (branch == "master" || branch.matches("branch-[\\d.]+".toRegex())) &&
            pr == "" &&
            gradle.taskGraph.hasTask(":artifactoryPublish")
    }
    val signingKeyId = project.findProperty("signingKeyId") as? String
    val signingKey = project.findProperty("signingKey") as? String
    val signingPassword = project.findProperty("signingPassword") as? String
    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    sign(configurations.archives.get())
}
