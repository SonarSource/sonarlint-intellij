
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

group = "org.sonarsource.sonarlint.intellij"
description = "SonarLint for IntelliJ IDEA"

val intellijBuildType: String by project
val intellijBuildVersion: String by project
val ideaHome: String? = System.getenv("IDEA_HOME")
val runIdeDirectory: String by project
val verifierEnv: String by project

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
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.NON_EXTENDABLE_API_USAGES,
            VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
            VerifyPluginTask.FailureLevel.PLUGIN_STRUCTURE_WARNINGS,
            VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
            VerifyPluginTask.FailureLevel.INVALID_PLUGIN
        )
        
        // Ignore expected compatibility problems for optional dependencies
        freeArgs = listOf(
            "-ignored-problems", "${project.rootDir}/.verifier-ignored-problems.txt"
        )

        val ideTypes = listOf(
            IntelliJPlatformType.AndroidStudio, IntelliJPlatformType.PyCharmCommunity, IntelliJPlatformType.PyCharmProfessional,
            IntelliJPlatformType.RubyMine, IntelliJPlatformType.CLion, IntelliJPlatformType.GoLand,
            IntelliJPlatformType.WebStorm, IntelliJPlatformType.PhpStorm, IntelliJPlatformType.Rider,
            IntelliJPlatformType.IntellijIdeaUltimate, IntelliJPlatformType.IntellijIdeaCommunity
        )

        ides {
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.2")
        }

        if (project.hasProperty("verifierEnv")) {
            when (verifierEnv) {
                "CI" -> ides {
                    select {
                        types = listOf(IntelliJPlatformType.IntellijIdeaCommunity)
                        channels = listOf(ProductRelease.Channel.RELEASE)
                        sinceBuild = "242.*"
                        untilBuild = "242.*"
                    }
                }

                "LATEST" -> ides {
                    select {
                        types = ideTypes
                        channels = listOf(ProductRelease.Channel.RELEASE)
                        sinceBuild = "252.*"
                        untilBuild = "252.*"
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
        // Hide any SonarQube CLI installed on the machine from the backend, so tests decide whether a CLI is installed:
        // it gets its own user home and a PATH without `sonar`, led by the directory the official installer adds to it.
        // On macOS the backend reads the system paths (/etc/paths) instead of PATH, which cannot be controlled.
        val sloopUserHome = layout.buildDirectory.dir("sloop-user-home").get().asFile
        // The backend splits SONARLINT_JVM_OPTS on spaces, so the user home can only be passed from a path without spaces.
        // Without it, the tests that need this isolation skip themselves.
        if (sloopUserHome.absolutePath.contains(' ')) {
            doFirst {
                logger.warn("Tests needing an isolated SonarQube for IDE backend are skipped: the build path contains spaces (${sloopUserHome.absolutePath})")
            }
        } else {
            systemProperty("sonarlint.test.sloop.user.home", sloopUserHome.absolutePath)
            environment("SONARLINT_JVM_OPTS", listOfNotNull(System.getenv("SONARLINT_JVM_OPTS")?.takeIf { it.isNotBlank() },
                "-Duser.home=${sloopUserHome.absolutePath}").joinToString(" "))
            // On Windows the official installer uses %LOCALAPPDATA%, which the backend reads from its environment
            val windows = System.getProperty("os.name").startsWith("Windows")
            val cliInstallDir = if (windows) {
                val localAppData = sloopUserHome.resolve("AppData/Local")
                environment("LOCALAPPDATA", localAppData.absolutePath)
                localAppData.resolve("sonarqube-cli/bin")
            } else {
                sloopUserHome.resolve(".local/share/sonarqube-cli/bin")
            }
            // Windows names it "Path"; a second "PATH" entry would make the one the backend sees unpredictable
            val pathVariable = System.getenv().keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
            // Agent CLIs are searched in ~/.local/bin too, except on Windows where only the path counts
            val agentBinDirectory = sloopUserHome.resolve(".local/bin")
            val fakeToolDirectories = listOfNotNull(cliInstallDir.absolutePath, agentBinDirectory.absolutePath.takeIf { windows })
            environment(pathVariable, (fakeToolDirectories + System.getenv(pathVariable).orEmpty().split(File.pathSeparator)
                .filterNot { dir -> listOf("sonar", "sonar.exe", "sonar.cmd").any { File(dir, it).exists() } })
                .joinToString(File.pathSeparator))
            doFirst {
                sloopUserHome.deleteRecursively()
                // The backend discovers agent CLIs once per process, at the first AI Integrations request of the run, so
                // the fake Claude Code used by AiIntegrationsTabLightTests must exist before any test runs
                agentBinDirectory.mkdirs()
                if (windows) {
                    agentBinDirectory.resolve("claude.cmd").writeText("@echo off\r\necho 2.0.0 (Claude Code)\r\n")
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
