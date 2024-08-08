import com.jetbrains.plugin.blockmap.core.BlockMap
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    alias(libs.plugins.kotlin)
    //alias(libs.plugins.intellij)
    alias(libs.plugins.intellij.migration)
    alias(libs.plugins.sonarqube)
    java
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
val pluginName = "sonarlint-intellij"

val sonarlintCoreVersion: String by project
val intellijBuildVersion: String by project
val omnisharpVersion: String by project

// The environment variables ARTIFACTORY_PRIVATE_USERNAME and ARTIFACTORY_PRIVATE_PASSWORD are used on CI env
// On local box, please add artifactoryUsername and artifactoryPassword to ~/.gradle/gradle.properties
val artifactoryUsername = System.getenv("ARTIFACTORY_PRIVATE_USERNAME")
    ?: (if (project.hasProperty("artifactoryUsername")) project.property("artifactoryUsername").toString() else "")
val artifactoryPassword = System.getenv("ARTIFACTORY_PRIVATE_PASSWORD")
    ?: (if (project.hasProperty("artifactoryPassword")) project.property("artifactoryPassword").toString() else "")

allprojects {
    apply {
        plugin("idea")
        plugin("java")
        plugin("org.jetbrains.intellij.platform")
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
        intellijPlatform {
            defaultRepositories()
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
        name = pluginName
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
    intellijPlatform {
        buildSearchableOptions = false
    }
}

val verifierVersions: String by project

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "223.8214.6"
        }
        name = pluginName
    }
    // updateSinceUntilBuild.set(false)

    pluginVerification {
        failureLevel = listOf(
            // these are the only issues we tolerate
            VerifyPluginTask.FailureLevel.DEPRECATED_API_USAGES,
            VerifyPluginTask.FailureLevel.EXPERIMENTAL_API_USAGES,
            VerifyPluginTask.FailureLevel.NOT_DYNAMIC,
            VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
            // TODO Workaround for CLion
            VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
            // needed because of CPPToolset.isRemote()
            VerifyPluginTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES
        )

        if (project.hasProperty("verifierVersions")) {
            ides {
                verifierVersions.split(',').forEach {
                    ide(it)
                }
            }
        } else {
            // Test oldest supported, and latest
            ides {
                ide("IC-2022.3.1")
                ide("IC-2024.1.4")
            }
        }

    }

    sonar {
        properties {
            property("sonar.projectName", "SonarLint for IntelliJ IDEA")
        }
    }

    artifactory {
        clientConfig.info.buildName = pluginName
        clientConfig.info.buildNumber = System.getenv("BUILD_ID")
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
                setProperty("password", System.getenv("ARTIFACTORY_DEPLOY_PASSWORD"))
            }
            defaults {
                setProperties(
                    mapOf(
                        "vcs.revision" to System.getenv("CIRRUS_CHANGE_IN_REPO"),
                        "vcs.branch" to (System.getenv("CIRRUS_BASE_BRANCH")
                            ?: System.getenv("CIRRUS_BRANCH")),
                        "build.name" to pluginName,
                        "build.number" to System.getenv("BUILD_ID")
                    )
                )
                publishConfigs("archives")
                setPublishPom(true)
                setPublishIvy(false)
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("sonarlint.telemetry.disabled", "true")
}

val runIdeDirectory: String by project

tasks.runIde {
    if (project.hasProperty("runIdeDirectory")) {
        jvmArgumentProviders += CommandLineArgumentProvider {
            listOf(
                "-DideDir=$runIdeDirectory",
                "-Dsonarlint.telemetry.disabled=true",
                // uncomment to customize the SonarCloud URL
                //"-Dsonarlint.internal.sonarcloud.url=https://sonarcloud.io/"
            )
        }
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
    intellijPlatform {
        intellijIdeaCommunity(intellijBuildVersion)
        bundledPlugins(providers.gradleProperty("platformPlugins").map { it.split(',') })
        pluginVerifier()
        instrumentationTools()
        testFramework(TestFrameworkType.Platform)
    }
    implementation(libs.sonarlint.java.client.legacy)
    implementation(libs.sonarlint.java.client.utils)
    implementation(libs.sonarlint.rpc.java.client)
    implementation(libs.commons.langs3)
    implementation(libs.commons.text)
    implementation(project(":common"))
    compileOnly(libs.findbugs.jsr305)
    runtimeOnly(project(":clion"))
    runtimeOnly(project(":clion-resharper"))
    runtimeOnly(project(":rider"))
    runtimeOnly(project(":git"))
    testImplementation(platform(libs.junit.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.okhttp3.mockwebserver) {
        exclude(module = "junit")
    }
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.awaitility)
    "sqplugins"(libs.bundles.sonar.analyzers)
    if (artifactoryUsername.isNotEmpty() && artifactoryPassword.isNotEmpty()) {
        "sqplugins"(libs.sonar.cfamily)
        "omnisharp"("org.sonarsource.sonarlint.omnisharp:omnisharp-roslyn:$omnisharpVersion:mono@zip")
        "omnisharp"("org.sonarsource.sonarlint.omnisharp:omnisharp-roslyn:$omnisharpVersion:net472@zip")
        "omnisharp"("org.sonarsource.sonarlint.omnisharp:omnisharp-roslyn:$omnisharpVersion:net6@zip")
    }
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    "sloop"("org.sonarsource.sonarlint.core:sonarlint-backend-cli:${libs.versions.sonarlint.core.get()}:no-arch@zip")
}

tasks {
    fun copyPlugins(destinationDir: File, name: String) {
        copy {
            from(project.configurations["sqplugins"])
            into(file("$destinationDir/${name}/plugins"))
        }
    }

    fun copyOmnisharp(destinationDir: File, name: String) {
        configurations["omnisharp"].resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
            copy {
                from(zipTree(artifact.file))
                into(file("$destinationDir/${name}/omnisharp/${artifact.classifier}"))
            }
        }
    }

    fun copySloop(destinationDir: File, name: String) {
        configurations["sloop"].resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
            copy {
                from(zipTree(artifact.file))
                into(file("$destinationDir/${name}/sloop/"))
            }
        }
    }

    prepareSandbox {
        doLast {
            copyPlugins(destinationDir, pluginName)
            copyOmnisharp(destinationDir, pluginName)
            copySloop(destinationDir, pluginName)
        }
    }

    prepareTestSandbox {
        doLast {
            copyPlugins(destinationDir, pluginName)
            copyOmnisharp(destinationDir, pluginName)
            copySloop(destinationDir, pluginName)
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

    jacocoTestReport {
        classDirectories.setFrom(files("build/instrumented/instrumentCode"))
        reports {
            xml.required.set(true)
        }
        dependsOn(check)
    }
}

tasks.artifactoryPublish {
    mustRunAfter(
        getTasksByName("cyclonedxBom", true),
        tasks.buildPlugin,
        getTasksByName("buildPluginBlockmap", true)
    )
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
