
import com.jetbrains.plugin.blockmap.core.BlockMap
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
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
    alias(libs.plugins.license)
    alias(libs.plugins.artifactory)
    idea
    signing
    alias(libs.plugins.cyclonedx)
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        "classpath"(group = "org.jetbrains.intellij", name = "blockmap", version = "1.0.7")
    }
}

group = "org.sonarsource.sonarlint.intellij"
description = "SonarLint for IntelliJ IDEA"

val intellijBuildVersion: String by project
val ideaHome: String? = System.getenv("IDEA_HOME")
val omnisharpVersion: String by project
val runIdeDirectory: String by project
val verifierEnv: String by project

// The environment variables ARTIFACTORY_ACCESS_USERNAME and ARTIFACTORY_ACCESS_TOKEN are used on CI env
// On local box, please add artifactoryUrl, artifactoryUsername and artifactoryPassword to ~/.gradle/gradle.properties
val artifactoryUrl = System.getenv("ARTIFACTORY_URL")
	?: (if (project.hasProperty("artifactoryUrl")) project.property("artifactoryUrl").toString() else "")
val artifactoryUsername = System.getenv("ARTIFACTORY_ACCESS_USERNAME")
    ?: (if (project.hasProperty("artifactoryUsername")) project.property("artifactoryUsername").toString() else "")
val artifactoryPassword = System.getenv("ARTIFACTORY_ACCESS_TOKEN")
    ?: (if (project.hasProperty("artifactoryPassword")) project.property("artifactoryPassword").toString() else "")

configurations {
    val sqplugins = create("sqplugins") { isTransitive = false }
    register("sqplugins_deps") {
        extendsFrom(sqplugins)
        isTransitive = true
    }
    create("cfamilySignature") { isTransitive = false }
    register("omnisharp")
    register("sloop")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    intellijPlatform {
        if (!ideaHome.isNullOrBlank()) {
            local(ideaHome)
        } else {
            intellijIdeaCommunity(intellijBuildVersion)
        }
        pluginComposedModule(implementation(project(":common")))
        pluginComposedModule(runtimeOnly(project(":clion")))
        pluginComposedModule(runtimeOnly(project(":clion-resharper")))
        pluginComposedModule(runtimeOnly(project(":nodejs")))
        pluginComposedModule(runtimeOnly(project(":rider")))
        pluginComposedModule(runtimeOnly(project(":git")))
        bundledPlugins("com.intellij.java", "Git4Idea")
        testFramework(TestFrameworkType.Platform)
        pluginVerifier("1.400")
    }
    implementation(libs.sonarlint.java.client.utils)
    implementation(libs.sonarlint.rpc.java.client)
    implementation(libs.sonarlint.rpc.impl)
    implementation(libs.commons.langs3)
    implementation(libs.commons.text)
    implementation(libs.bouncycastle.bcpg)
    implementation(libs.bouncycastle.bcprov)
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
    if (artifactoryUrl.isNotEmpty() && artifactoryUsername.isNotEmpty() && artifactoryPassword.isNotEmpty()) {
        "cfamilySignature"("${libs.sonar.cfamily.get()}@jar.asc")
        "sqplugins"(libs.sonar.dotnet.enterprise)
        "omnisharp"("org.sonarsource.sonarlint.omnisharp:omnisharp-roslyn:$omnisharpVersion:mono@zip")
        "omnisharp"("org.sonarsource.sonarlint.omnisharp:omnisharp-roslyn:$omnisharpVersion:net472@zip")
        "omnisharp"("org.sonarsource.sonarlint.omnisharp:omnisharp-roslyn:$omnisharpVersion:net6@zip")
    }
    "sloop"("org.sonarsource.sonarlint.core:sonarlint-backend-cli:${libs.versions.sonarlint.core.get()}:no-arch@zip")
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

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "231.9423.4"
            untilBuild = provider { null }
        }
    }
    buildSearchableOptions = true
    pluginVerification {
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.NON_EXTENDABLE_API_USAGES,
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
            ide(IntelliJPlatformType.IntellijIdeaCommunity, "2025.2")
        }

        if (project.hasProperty("verifierEnv")) {
            when (verifierEnv) {
                "CI" -> ides {
                    select {
                        types = listOf(IntelliJPlatformType.IntellijIdeaCommunity)
                        channels = listOf(ProductRelease.Channel.RELEASE)
                        sinceBuild = "231.*"
                        untilBuild = "231.*"
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
        clientConfig.info.buildName = "sonarlint-intellij"
        clientConfig.info.buildNumber = System.getenv("BUILD_NUMBER")
        clientConfig.isIncludeEnvVars = true
        clientConfig.envVarsExcludePatterns =
            "*password*,*PASSWORD*,*secret*,*MAVEN_CMD_LINE_ARGS*,sun.java.command,*token*,*TOKEN*,*LOGIN*,*login*,*key*,*KEY*,*PASSPHRASE*,*signing*"
        clientConfig.info.addEnvironmentProperty(
            "ARTIFACTS_TO_DOWNLOAD",
            "org.sonarsource.sonarlint.intellij:sonarlint-intellij:zip,org.sonarsource.sonarlint.intellij:sonarlint-intellij:json:cyclonedx"
        )
        setContextUrl(System.getenv("ARTIFACTORY_URL"))
        publish {
            repository {
                setProperty("repoKey", System.getenv("ARTIFACTORY_DEPLOY_REPO"))
                setProperty("username", System.getenv("ARTIFACTORY_DEPLOY_USERNAME"))
                setProperty("password", System.getenv("ARTIFACTORY_DEPLOY_ACCESS_TOKEN"))
            }
            defaults {
                setProperties(
                    mapOf(
                        "vcs.revision" to System.getenv("GITHUB_SHA"),
                        "vcs.branch" to (System.getenv("GITHUB_HEAD_REF")
                            ?: System.getenv("GITHUB_REF_NAME")),
                        "build.name" to "sonarlint-intellij",
                        "build.number" to System.getenv("BUILD_NUMBER")
                    )
                )
                publishConfigs("archives")
                setPublishPom(true)
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

    copyAsc(pluginsDir)
    copyPlugins(pluginsDir)
    renameCsharpPlugins(pluginsDir)
    copyOmnisharp(destinationDir, pluginName)
    copySloop(destinationDir, pluginName)
    unzipEslintBridgeBundle(pluginsDir)
}

fun copyAsc(pluginsDir: File) {
    copy {
        from(project.configurations["cfamilySignature"])
        into(pluginsDir)
    }
}

fun copyPlugins(pluginsDir: File) {
    copy {
        from(project.configurations["sqplugins"])
        into(pluginsDir)
    }
}

fun renameCsharpPlugins(pluginsDir: File) {
    pluginsDir.listFiles()?.forEach { file ->
        when {
            file.name.matches(Regex("sonar-csharp-enterprise-plugin-.*\\.jar")) -> {
                file.renameTo(File(pluginsDir, "sonar-csharp-enterprise-plugin.jar"))
            }

            file.name.matches(Regex("sonar-csharp-plugin-.*\\.jar")) -> {
                file.renameTo(File(pluginsDir, "sonar-csharp-plugin.jar"))
            }
        }
    }
}

fun copyOmnisharp(destinationDir: File, pluginName: Property<String>) {
    configurations["omnisharp"].resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
        copy {
            from(zipTree(artifact.file))
            into(file("$destinationDir/${pluginName.get()}/omnisharp/${artifact.classifier}"))
        }
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

configurations.archives.get().isCanBeResolved = true

tasks {
    processResources {
        val cfamilyVersion = providers.provider { libs.versions.sonar.cpp.get() }
        inputs.property("cfamilyVersion", cfamilyVersion)
        filesMatching("cfamily-version.properties") {
            expand(mapOf("cfamilyVersion" to cfamilyVersion.get()))
        }
    }

    compileKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    compileTestKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
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
        systemProperty("sonarlint.telemetry.disabled", "true")
        systemProperty("sonarlint.monitoring.disabled", "true")
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

    cyclonedxBom {
        setIncludeConfigs(listOf("runtimeClasspath", "sqplugins_deps"))
        inputs.files(configurations.runtimeClasspath, configurations.archives.get())
        mustRunAfter(
            getTasksByName("buildPluginBlockmap", true)
        )
    }

    val buildPluginBlockmap by registering {
        inputs.file(buildPlugin.get().archiveFile)
        doLast {
            val distribZip = buildPlugin.get().archiveFile.get().asFile
            artifacts.add("archives", distribZip) {
                name = project.name
                extension = "zip"
                type = "zip"
                builtBy("buildPluginBlockmap")
            }
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
            artifacts.add("archives", blockMapFileZipFile) {
                name = project.name
                extension = "zip.blockmap.zip"
                type = "zip"
                builtBy("buildPluginBlockmap")
            }
            val fileHash = com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(com.jetbrains.plugin.blockmap.core.FileHash(distribZip.inputStream()))
            val fileHashJsonFile = file(distribZip.absolutePath + ".hash.json")
            fileHashJsonFile.writeText(fileHash)
            artifacts.add("archives", fileHashJsonFile) {
                name = project.name
                extension = "zip.hash.json"
                type = "json"
                builtBy("buildPluginBlockmap")
            }
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

    signing {
        setRequired {
            val branch = System.getenv("GITHUB_REF_NAME") ?: ""
            val pr = System.getenv("GITHUB_HEAD_REF") ?: ""
            (branch == "master" || branch.matches("branch-[\\d.]+".toRegex())) &&
                pr == "" &&
                gradle.taskGraph.hasTask(":artifactoryPublish")
        }
        val signingKeyId: String? by project
        val signingKey: String? by project
        val signingPassword: String? by project
        useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
        sign(configurations.archives.get())
    }
}
