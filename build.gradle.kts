
import com.jetbrains.plugin.blockmap.core.BlockMap
import groovy.lang.GroovyObject
import org.jetbrains.intellij.tasks.RunPluginVerifierTask
import org.jfrog.gradle.plugin.artifactory.dsl.PublisherConfig
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.EnumSet
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins {
    kotlin("jvm") version "1.8.10"
    id("org.jetbrains.intellij") version "1.15.0"
    id("org.sonarqube") version "4.4.1.3373"
    java
    jacoco
    id("com.github.hierynomus.license") version "0.16.1"
    id("com.jfrog.artifactory") version "4.30.1"
    id("com.google.protobuf") version "0.9.4"
    idea
    signing
    id("org.cyclonedx.bom") version "1.7.4"
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

val sonarlintCoreVersion: String by project
val protobufVersion: String by project
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
        plugin("org.jetbrains.intellij")
        plugin("org.cyclonedx.bom")
        plugin("com.github.hierynomus.license")
    }

    repositories {
        maven("https://repox.jfrog.io/repox/sonarsource") {
            if (artifactoryUsername.isNotEmpty() && artifactoryPassword.isNotEmpty()) {
                credentials {
                    username = artifactoryUsername
                    password = artifactoryPassword
                }
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
            listOf("**/*.jar", "**/*.png", "**/README", "**/proto/*.java")
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
    version.set(intellijBuildVersion)
    pluginName.set("sonarlint-intellij")
    updateSinceUntilBuild.set(false)
    plugins.set(listOf("java", "Git4Idea"))
}

val verifierVersions: String by project

tasks.runPluginVerifier {
    if (project.hasProperty("verifierVersions")) {
        ideVersions.set(verifierVersions.split(','))
    } else {
        // Test oldest supported, and latest
        ideVersions.set(listOf("IC-2022.3.1", "IC-2023.2.1"))
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

protobuf {
    // Configure the protoc executable
    protoc {
        // Download from repositories. Must be the same as the one used in sonarlint-core
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
}

tasks.test {
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
    useJUnitPlatform()
    systemProperty("sonarlint.telemetry.disabled", "true")
}

val runIdeDirectory: String by project

tasks.runIde {
    systemProperty("sonarlint.telemetry.disabled", "true")
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
}

dependencies {
    implementation("org.sonarsource.sonarlint.core:sonarlint-core:$sonarlintCoreVersion")
    implementation("commons-lang:commons-lang:2.6")
    implementation(project(":common"))
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
    runtimeOnly(project(":clion"))
    runtimeOnly(project(":rider"))
    runtimeOnly(project(":git"))
    testImplementation(platform("org.junit:junit-bom:5.9.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.11.0") {
        exclude(module = "junit")
    }
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
    testImplementation("org.awaitility:awaitility:4.2.0")
    "sqplugins"("org.sonarsource.java:sonar-java-plugin:7.30.1.34514")
    "sqplugins"("org.sonarsource.javascript:sonar-javascript-plugin:10.11.0.25043")
    "sqplugins"("org.sonarsource.php:sonar-php-plugin:3.33.0.11274")
    "sqplugins"("org.sonarsource.python:sonar-python-plugin:4.14.0.14263")
    "sqplugins"("org.sonarsource.kotlin:sonar-kotlin-plugin:2.20.0.4382")
    "sqplugins"("org.sonarsource.slang:sonar-ruby-plugin:1.15.0.4655")
    "sqplugins"("org.sonarsource.html:sonar-html-plugin:3.9.0.3600")
    "sqplugins"("org.sonarsource.xml:sonar-xml-plugin:2.10.0.4108")
    "sqplugins"("org.sonarsource.sonarlint.omnisharp:sonarlint-omnisharp-plugin:1.18.0.83276")
    "sqplugins"("org.sonarsource.text:sonar-text-plugin:2.7.1.1388")
    "sqplugins"("org.sonarsource.slang:sonar-go-plugin:1.15.0.4655")
    "sqplugins"("org.sonarsource.iac:sonar-iac-plugin:1.25.0.8192")
    if (artifactoryUsername.isNotEmpty() && artifactoryPassword.isNotEmpty()) {
        "sqplugins"("com.sonarsource.cpp:sonar-cfamily-plugin:6.52.0.66181")
    }
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    constraints {
        testImplementation("com.squareup.okio:okio-jvm:3.4.0") {
            because("this transitive dependency of okhttp3 has a high severity vulnerability not yet patched")
        }
    }
    "omnisharp"("org.sonarsource.sonarlint.omnisharp:omnisharp-roslyn:$omnisharpVersion:mono@zip")
    "omnisharp"("org.sonarsource.sonarlint.omnisharp:omnisharp-roslyn:$omnisharpVersion:net472@zip")
    "omnisharp"("org.sonarsource.sonarlint.omnisharp:omnisharp-roslyn:$omnisharpVersion:net6@zip")
}

tasks {

    fun copyPlugins(destinationDir: File, pluginName: Property<String>) {
        copy {
            from(project.configurations["sqplugins"])
            into(file("$destinationDir/${pluginName.get()}/plugins"))
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

    prepareSandbox {
        doLast {
            copyPlugins(destinationDir, pluginName)
            copyOmnisharp(destinationDir, pluginName)
        }
    }

    prepareTestingSandbox {
        doLast {
            copyPlugins(destinationDir, pluginName)
            copyOmnisharp(destinationDir, pluginName)
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
    publish(delegateClosureOf<PublisherConfig> {
        repository(delegateClosureOf<GroovyObject> {
            setProperty("repoKey", System.getenv("ARTIFACTORY_DEPLOY_REPO"))
            setProperty("username", System.getenv("ARTIFACTORY_DEPLOY_USERNAME"))
            setProperty("password", System.getenv("ARTIFACTORY_DEPLOY_PASSWORD"))
        })
        defaults(delegateClosureOf<GroovyObject> {
            setProperty(
                "properties", mapOf(
                "vcs.revision" to System.getenv("CIRRUS_CHANGE_IN_REPO"),
                "vcs.branch" to (System.getenv("CIRRUS_BASE_BRANCH")
                    ?: System.getenv("CIRRUS_BRANCH")),
                "build.name" to "sonarlint-intellij",
                "build.number" to System.getenv("BUILD_ID")
            )
            )
            invokeMethod("publishConfigs", "archives")
            setProperty("publishPom", true) // Publish generated POM files to Artifactory (true by default)
            setProperty("publishIvy", false) // Publish generated Ivy descriptor files to Artifactory (true by default)
        })
    })
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
