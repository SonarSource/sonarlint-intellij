
import com.jetbrains.plugin.blockmap.core.BlockMap
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.EnumSet
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.jetbrains.intellij.tasks.RunPluginVerifierTask

plugins {
    java
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellij)
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
        "classpath"(group = "org.jetbrains.intellij", name = "blockmap", version = "1.0.6")
    }
}

group = "org.sonarsource.sonarlint.intellij"
description = "SonarLint for IntelliJ IDEA"

val intellijBuildVersion: String by project
val omnisharpVersion: String by project

// The environment variables ARTIFACTORY_PRIVATE_USERNAME and ARTIFACTORY_PRIVATE_PASSWORD are used on CI env
// On local box, please add artifactoryUsername and artifactoryPassword to ~/.gradle/gradle.properties
val artifactoryUsername = System.getenv("ARTIFACTORY_PRIVATE_USERNAME")
    ?: (if (project.hasProperty("artifactoryUsername")) project.property("artifactoryUsername").toString() else "")
val artifactoryPassword = System.getenv("ARTIFACTORY_PRIVATE_PASSWORD")
    ?: (if (project.hasProperty("artifactoryPassword")) project.property("artifactoryPassword").toString() else "")

val ideaHome: String? = System.getenv("IDEA_HOME")

allprojects {
    apply {
        plugin("idea")
        plugin("java")
        plugin("org.jetbrains.intellij")
        plugin("org.cyclonedx.bom")
        plugin("com.github.hierynomus.license")
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
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            apiVersion = "1.7"
            jvmTarget = "17"
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
}

subprojects {
    tasks {
        buildSearchableOptions {
            // the only module contributing settings is the root one
            enabled = false
        }
    }
}

intellij {
    pluginName.set("sonarlint-intellij")
    updateSinceUntilBuild.set(false)
    plugins.set(listOf("java", "Git4Idea"))
    if (!ideaHome.isNullOrBlank()) {
        println("Using local installation of IntelliJ IDEA: $ideaHome")
        localPath.set(ideaHome)
        localSourcesPath.set(ideaHome)
    } else {
        println("No local installation of IntelliJ IDEA found, using version $intellijBuildVersion")
        version.set(intellijBuildVersion)
    }
}

val verifierVersions: String by project

tasks.runPluginVerifier {
    if (project.hasProperty("verifierVersions")) {
        ideVersions.set(verifierVersions.split(','))
    } else {
        // Test oldest supported, and latest
        ideVersions.set(listOf("IC-2022.3.1", "IC-2024.3.1"))
    }
    failureLevel.set(
        EnumSet.complementOf(
            EnumSet.of(
                // these are the only issues we tolerate
                RunPluginVerifierTask.FailureLevel.DEPRECATED_API_USAGES,
                RunPluginVerifierTask.FailureLevel.EXPERIMENTAL_API_USAGES,
                RunPluginVerifierTask.FailureLevel.NOT_DYNAMIC,
                RunPluginVerifierTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
                // TODO Workaround for CLion
                RunPluginVerifierTask.FailureLevel.MISSING_DEPENDENCIES,
                // needed because of CPPToolset.isRemote()
                RunPluginVerifierTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES
            )
        )
    )
}

tasks.test {
    useJUnitPlatform()
    systemProperty("sonarlint.telemetry.disabled", "true")
    systemProperty("sonarlint.monitoring.disabled", "true")
    doNotTrackState("Tests should always run")
}

val runIdeDirectory: String by project

tasks.runIde {
    systemProperty("sonarlint.telemetry.disabled", "true")
    systemProperty("sonarlint.monitoring.disabled", "true")
    // uncomment to customize the SonarCloud URL
    //systemProperty("sonarlint.internal.sonarcloud.url", "https://sonarcloud.io/")
    if (project.hasProperty("runIdeDirectory")) {
        ideDir.set(File(runIdeDirectory))
    }
    maxHeapSize = "2g"
}

configurations {
    val sqplugins = create("sqplugins") { isTransitive = false }
    create("sqplugins_deps") {
        extendsFrom(sqplugins)
        isTransitive = true
    }
    create("omnisharp")
    create("sloop")
}

dependencies {
    implementation(libs.sonarlint.java.client.utils)
    implementation(libs.sonarlint.rpc.java.client)
    implementation(libs.sonarlint.rpc.impl)
    implementation(libs.commons.langs3)
    implementation(libs.commons.text)
    implementation(project(":common"))
    compileOnly(libs.findbugs.jsr305)
    runtimeOnly(project(":clion"))
    runtimeOnly(project(":clion-resharper"))
    runtimeOnly(project(":nodejs"))
    runtimeOnly(project(":rider"))
    runtimeOnly(project(":git"))
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.awaitility)
    "sqplugins"(libs.bundles.sonar.analyzers)
    if (artifactoryUsername.isNotEmpty() && artifactoryPassword.isNotEmpty()) {
        "sqplugins"(libs.sonar.cfamily)
        "sqplugins"(libs.sonar.dotnet.enterprise)
        "omnisharp"("org.sonarsource.sonarlint.omnisharp:omnisharp-roslyn:$omnisharpVersion:mono@zip")
        "omnisharp"("org.sonarsource.sonarlint.omnisharp:omnisharp-roslyn:$omnisharpVersion:net472@zip")
        "omnisharp"("org.sonarsource.sonarlint.omnisharp:omnisharp-roslyn:$omnisharpVersion:net6@zip")
    }
    "sloop"("org.sonarsource.sonarlint.core:sonarlint-backend-cli:${libs.versions.sonarlint.core.get()}:no-arch@zip")
}

tasks {
    fun copyPlugins(destinationDir: File, pluginName: Property<String>) {
        copy {
            from(project.configurations["sqplugins"])
            into(file("$destinationDir/${pluginName.get()}/plugins"))
        }
    }

    fun renameCsharpPlugins(destinationDir: File, pluginName: Property<String>) {
        val pluginsDir = File("$destinationDir/${pluginName.get()}/plugins")
        pluginsDir.listFiles()?.forEach { file ->
            if (file.name.matches(Regex("sonar-csharp-enterprise-plugin-.*\\.jar"))) {
                file.renameTo(File(pluginsDir, "sonar-csharp-enterprise-plugin.jar"))
            } else if (file.name.matches(Regex("sonar-csharp-plugin-.*\\.jar"))) {
                file.renameTo(File(pluginsDir, "sonar-csharp-plugin.jar"))
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

    fun unzipEslintBridgeBundle(destinationDir: File, pluginName: Property<String>) {
        val pluginsDir = File("$destinationDir/${pluginName.get()}/plugins")
        val jarPath = pluginsDir.listFiles()?.find {
            it.name.startsWith("sonar-javascript-plugin-") && it.name.endsWith(".jar")
        } ?: throw GradleException("sonar-javascript-plugin-* JAR not found in $destinationDir")

        val zipFile = ZipFile(jarPath)
        val entry = zipFile.entries().asSequence().find { it.name.matches(Regex("sonarjs-.*\\.tgz")) }
            ?: throw GradleException("eslint bridge server bundle not found in JAR $jarPath")


        val outputFolderPath = Paths.get("$pluginsDir/eslint-bridge")
        val outputFilePath = outputFolderPath.resolve(entry.name)

        if (!Files.exists(outputFolderPath)) {
            Files.createDirectory(outputFolderPath)
        }

        zipFile.getInputStream(entry).use { input ->
            FileOutputStream(outputFilePath.toFile()).use { output ->
                input.copyTo(output)
            }
        }

        GzipCompressorInputStream(FileInputStream(outputFilePath.toFile())).use { gzipInput ->
            TarArchiveInputStream(gzipInput).use { tarInput ->
                var tarEntry: TarArchiveEntry?
                while (tarInput.nextEntry.also { tarEntry = it } != null) {
                    val outputFile = outputFolderPath.resolve(tarEntry!!.name).toFile()
                    if (tarEntry!!.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile.mkdirs()
                        FileOutputStream(outputFile).use { output ->
                            tarInput.copyTo(output)
                        }
                    }
                }
            }
        }

        Files.delete(outputFilePath)
    }

    prepareSandbox {
        doLast {
            copyPlugins(destinationDir, pluginName)
            renameCsharpPlugins(destinationDir, pluginName)
            copyOmnisharp(destinationDir, pluginName)
            copySloop(destinationDir, pluginName)
            unzipEslintBridgeBundle(destinationDir, pluginName)
        }
    }

    prepareTestingSandbox {
        doLast {
            copyPlugins(destinationDir, pluginName)
            renameCsharpPlugins(destinationDir, pluginName)
            copyOmnisharp(destinationDir, pluginName)
            copySloop(destinationDir, pluginName)
            unzipEslintBridgeBundle(destinationDir, pluginName)
        }
    }

    val buildPluginBlockmap by registering {
        inputs.file(buildPlugin.get().archiveFile)
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
}

tasks.artifactoryPublish {
    mustRunAfter(
        getTasksByName("cyclonedxBom", true),
        tasks.buildPlugin,
        getTasksByName("buildPluginBlockmap", true)
    )
}

sonar {
    properties {
        property("sonar.projectName", "SonarLint for IntelliJ IDEA")
    }
}

artifactory {
    clientConfig.info.buildName = "sonarlint-intellij"
    clientConfig.info.buildNumber = System.getenv("BUILD_ID")
    clientConfig.isIncludeEnvVars = true
    clientConfig.envVarsExcludePatterns = "*password*,*PASSWORD*,*secret*,*MAVEN_CMD_LINE_ARGS*,sun.java.command,*token*,*TOKEN*,*LOGIN*,*login*,*key*,*KEY*,*PASSPHRASE*,*signing*"
    clientConfig.info.addEnvironmentProperty(
        "ARTIFACTS_TO_DOWNLOAD",
        "org.sonarsource.sonarlint.intellij:sonarlint-intellij:zip,org.sonarsource.sonarlint.intellij:sonarlint-intellij:json:cyclonedx"
    )
    setContextUrl(System.getenv("ARTIFACTORY_URL"))
    publish {
        repository {
            setProperty("repoKey", System.getenv("ARTIFACTORY_DEPLOY_REPO"))
            setProperty("username", System.getenv("ARTIFACTORY_DEPLOY_USERNAME"))
            setProperty("password", System.getenv("ARTIFACTORY_DEPLOY_PASSWORD"))
        }
        defaults {
            setProperties(
                mapOf(
                    "vcs.revision" to System.getenv("CIRRUS_CHANGE_IN_REPO"),
                    "vcs.branch" to (System.getenv("CIRRUS_BASE_BRANCH")
                        ?: System.getenv("CIRRUS_BRANCH")),
                    "build.name" to "sonarlint-intellij",
                    "build.number" to System.getenv("BUILD_ID")
                )
            )
            publishConfigs("archives")
            setPublishPom(true)
            setPublishIvy(false)
        }
    }
}

signing {
    setRequired {
        val branch = System.getenv("CIRRUS_BRANCH") ?: ""
        val pr = System.getenv("CIRRUS_PR") ?: ""
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
