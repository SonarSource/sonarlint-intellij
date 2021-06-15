import org.jetbrains.intellij.tasks.RunPluginVerifierTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.EnumSet
import com.google.protobuf.gradle.*
import com.jetbrains.plugin.blockmap.core.BlockMap
import groovy.lang.GroovyObject
import org.jfrog.gradle.plugin.artifactory.dsl.PublisherConfig
import java.util.zip.ZipOutputStream
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.BufferedInputStream
import java.util.zip.ZipEntry

plugins {
    kotlin("jvm") version "1.4.30"
    id("org.jetbrains.intellij") version "0.7.3"
    id("org.sonarqube") version "3.1.1"
    java
    jacoco
    id("com.github.hierynomus.license") version "0.15.0"
    id("com.jfrog.artifactory") version "4.21.0"
    id("com.google.protobuf") version "0.8.16"
    idea
    signing
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        "classpath"(group = "org.jetbrains.intellij", name = "blockmap", version = "1.0.5")
    }
}

group = "org.sonarsource.sonarlint.intellij"
description = "SonarLint for IntelliJ IDEA"

val sonarlintCoreVersion: String by project
val protobufVersion: String by project
val typescriptVersion: String by project
val jettyVersion: String by project
val intellijBuildVersion: String by project

// The environment variables ARTIFACTORY_PRIVATE_USERNAME and ARTIFACTORY_PRIVATE_PASSWORD are used on CI env (Azure)
// On local box, please add artifactoryUsername and artifactoryPassword to ~/.gradle/gradle.properties
val artifactoryUsername = System.getenv("ARTIFACTORY_PRIVATE_READER_USERNAME") ?: (if (project.hasProperty("artifactoryUsername")) project.property("artifactoryUsername").toString() else "")
val artifactoryPassword = System.getenv("ARTIFACTORY_PRIVATE_READER_PASSWORD") ?: (if (project.hasProperty("artifactoryPassword")) project.property("artifactoryPassword").toString() else "")

allprojects {
    apply {
        plugin("idea")
        plugin("java")
        plugin("org.jetbrains.intellij")
    }

    repositories {
        mavenCentral()
        maven("https://repox.jfrog.io/repox/sonarsource") {
            content { excludeGroup("typescript") }
            if (artifactoryUsername.isNotEmpty() && artifactoryPassword.isNotEmpty()) {
                credentials {
                    username = artifactoryUsername
                    password = artifactoryPassword
                }
            }
        }
        ivy("https://repox.jfrog.io/repox/api/npm/npm") {
            patternLayout {
                artifact("[organization]/-/[module]-[revision].[ext]")
                metadataSources { artifact() }
            }
            content { includeGroup("typescript") }
        }
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(8))
        }
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            apiVersion = "1.3"
        }
    }
}

intellij {
    version = intellijBuildVersion
    pluginName = "sonarlint-intellij"
    updateSinceUntilBuild = false
    setPlugins("java")
}

tasks.runPluginVerifier {
    // Test oldest supported, and latest
    setIdeVersions(listOf("IC-2019.3.5", "IC-2021.1"))
    setFailureLevel(
        EnumSet.complementOf(
            EnumSet.of(
                // these are the only issues we tolerate
                RunPluginVerifierTask.FailureLevel.DEPRECATED_API_USAGES,
                RunPluginVerifierTask.FailureLevel.EXPERIMENTAL_API_USAGES,
                RunPluginVerifierTask.FailureLevel.NOT_DYNAMIC,
                RunPluginVerifierTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
                // Workaround for Module.getModuleFilePath() in 2019.3
                RunPluginVerifierTask.FailureLevel.INTERNAL_API_USAGES,
                // TODO Workaround for CLion
                RunPluginVerifierTask.FailureLevel.MISSING_DEPENDENCIES
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

// The protobuf version embedded into the IntelliJ distribution is conflicting with the one we use in our plugin, so we have to exclude it
// Note that the version (3.5.1) may change when updating the traget IDE we use for compilation, so this will have to be kept up to date.
project.afterEvaluate {
    sourceSets {
        main {
            compileClasspath -= files(File(intellij.ideaDependency.classes, "lib/protobuf-java-3.5.1.jar").getAbsolutePath())
        }
        test {
            runtimeClasspath -= files(File(intellij.ideaDependency.classes, "lib/protobuf-java-3.5.1.jar").getAbsolutePath())
        }
    }
}

tasks.test {
    useJUnit()
    systemProperty("sonarlint.telemetry.disabled", "true")
}

tasks.runIde {
    systemProperty("sonarlint.telemetry.disabled", "true")
    if (project.hasProperty("runIdeDirectory")) {
        ideDirectory(project.property("runIdeDirectory"))
    }
}

configurations {
    create("sqplugins") { isTransitive = false }
    create("typescript") { isCanBeConsumed = false }
}

dependencies {
    implementation("org.sonarsource.sonarlint.core:sonarlint-core:$sonarlintCoreVersion")
    implementation("commons-lang:commons-lang:2.6")
    compileOnly("com.google.code.findbugs:jsr305:2.0.2")
    implementation ("org.apache.httpcomponents.client5:httpclient5:5.0.3") {
        exclude(module = "slf4j-api")
    }
    implementation(project(":clion"))
    implementation(project(":common"))
    testImplementation("junit:junit:4.12")
    testImplementation("org.assertj:assertj-core:3.16.1")
    testImplementation("org.mockito:mockito-core:2.19.0")
    testImplementation("org.eclipse.jetty:jetty-server:$jettyVersion")
    testImplementation("org.eclipse.jetty:jetty-servlet:$jettyVersion")
    testImplementation("org.eclipse.jetty:jetty-proxy:$jettyVersion")
    "sqplugins"("org.sonarsource.java:sonar-java-plugin:6.15.1.26025@jar")
    "sqplugins"("org.sonarsource.javascript:sonar-javascript-plugin:7.4.2.15501@jar")
    "sqplugins"("org.sonarsource.php:sonar-php-plugin:3.17.0.7439@jar")
    "sqplugins"("org.sonarsource.python:sonar-python-plugin:3.5.0.8244@jar")
    "sqplugins"("org.sonarsource.slang:sonar-kotlin-plugin:1.8.3.2219@jar")
    "sqplugins"("org.sonarsource.slang:sonar-ruby-plugin:1.8.3.2219@jar")
    "sqplugins"("org.sonarsource.html:sonar-html-plugin:3.4.0.2754@jar")
    if (artifactoryUsername.isNotEmpty() && artifactoryPassword.isNotEmpty()) {
        "sqplugins"("com.sonarsource.cpp:sonar-cfamily-plugin:6.21.0.32709@jar")
    }
    "typescript"("typescript:typescript:$typescriptVersion@tgz")
}

fun copyPlugins(destinationDir: File, pluginName: String) {
    val tsBundlePath = project.configurations.get("typescript").iterator().next()
    copy {
        from(tarTree(tsBundlePath))
        exclude(
            "**/loc/**",
            "**/lib/*/diagnosticMessages.generated.json"
        )
        into(file("$destinationDir/$pluginName"))
    }
    file("$destinationDir/$pluginName/package").renameTo(file("$destinationDir/$pluginName/typescript"))
    copy {
        from(project.configurations.get("sqplugins"))
        into(file("$destinationDir/$pluginName/plugins"))
    }
}

tasks.prepareTestingSandbox {
    doLast {
        copyPlugins(destinationDir, pluginName)
    }
}

tasks.prepareSandbox {
    doLast {
        copyPlugins(destinationDir, pluginName)
    }
}

val buildPluginBlockmap by tasks.registering {
    inputs.file(tasks.buildPlugin.get().archiveFile)
    doLast {
        val distribZip = tasks.buildPlugin.get().archiveFile.get().asFile
        val blockMapBytes = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(BlockMap(distribZip.inputStream()))
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
        val fileHash = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(com.jetbrains.plugin.blockmap.core.FileHash(distribZip.inputStream()))
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

tasks.buildPlugin {
    finalizedBy(buildPluginBlockmap)
}

sonarqube {
    properties {
        property("sonar.projectName", "SonarLint for IntelliJ IDEA")
    }
}

license {
    mapping(
        mapOf(
            "java" to "SLASHSTAR_STYLE",
            "kt" to "SLASHSTAR_STYLE"
        )
    )
    strictCheck = true
}

tasks.jacocoTestReport {
    classDirectories.setFrom(files("build/classes/java/main-instrumented"))
    reports {
        xml.setEnabled(true)
    }
}

artifactory {
    clientConfig.info.setBuildName("sonarlint-intellij")
    clientConfig.info.setBuildNumber(System.getenv("BUILD_BUILDID"))
    clientConfig.setIncludeEnvVars(true)
    clientConfig.setEnvVarsExcludePatterns("*password*,*PASSWORD*,*secret*,*MAVEN_CMD_LINE_ARGS*,sun.java.command,*token*,*TOKEN*,*LOGIN*,*login*,*key*,*KEY*,*PASSPHRASE*,*signing*")
    clientConfig.info.addEnvironmentProperty(
        "ARTIFACTS_TO_DOWNLOAD",
        "org.sonarsource.sonarlint.intellij:sonarlint-intellij:zip"
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
                    "vcs.revision" to System.getenv("BUILD_SOURCEVERSION"),
                    "vcs.branch" to (System.getenv("SYSTEM_PULLREQUEST_TARGETBRANCH")
                        ?: System.getenv("BUILD_SOURCEBRANCHNAME")),
                    "build.name" to "sonarlint-intellij",
                    "build.number" to System.getenv("BUILD_BUILDID")
                )
            )
            invokeMethod("publishConfigs", "archives")
            setProperty("publishPom", true) // Publish generated POM files to Artifactory (true by default)
            setProperty("publishIvy", false) // Publish generated Ivy descriptor files to Artifactory (true by default)
        })
    })
}

signing {
    setRequired({
        gradle.taskGraph.hasTask(":artifactoryPublish") && System.getenv("SYSTEM_PULLREQUEST_TARGETBRANCH") == null;
    })
    sign(configurations.archives.get())
}


