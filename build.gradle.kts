import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc
import com.jetbrains.plugin.blockmap.core.BlockMap
import de.undercouch.gradle.tasks.download.Download
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
    kotlin("jvm") version "1.7.10"
    id("org.jetbrains.intellij") version "1.9.0"
    id("org.sonarqube") version "3.4.0.2513"
    java
    jacoco
    id("com.github.hierynomus.license") version "0.16.1"
    id("com.jfrog.artifactory") version "4.29.0"
    id("com.google.protobuf") version "0.8.19"
    idea
    signing
    id("de.undercouch.download") version "5.2.0"
    id("org.cyclonedx.bom") version "1.7.1"
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
val jettyVersion: String by project
val intellijBuildVersion: String by project
val omnisharpVersion: String by project

// The environment variables ARTIFACTORY_PRIVATE_USERNAME and ARTIFACTORY_PRIVATE_PASSWORD are used on CI env (Azure)
// On local box, please add artifactoryUsername and artifactoryPassword to ~/.gradle/gradle.properties
val artifactoryUsername = System.getenv("ARTIFACTORY_PRIVATE_READER_USERNAME")
    ?: (if (project.hasProperty("artifactoryUsername")) project.property("artifactoryUsername").toString() else "")
val artifactoryPassword = System.getenv("ARTIFACTORY_PRIVATE_READER_PASSWORD")
    ?: (if (project.hasProperty("artifactoryPassword")) project.property("artifactoryPassword").toString() else "")

allprojects {
    apply {
        plugin("idea")
        plugin("java")
        plugin("org.jetbrains.intellij")
        plugin("org.cyclonedx.bom")
    }

    repositories {
        mavenCentral()
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
            languageVersion.set(JavaLanguageVersion.of(11))
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            apiVersion = "1.7"
            jvmTarget = "11"
        }
    }

    tasks.cyclonedxBom {
        setIncludeConfigs(listOf("runtimeClasspath", "sqplugins_deps"))
    }

    val bomFile = layout.buildDirectory.file("reports/bom.json")
    artifacts.add("archives", bomFile.get().asFile) {
        name = "sonarlint-intellij"
        type = "json"
        classifier = "cyclonedx"
        builtBy("cyclonedxBom")
    }
}

intellij {
    version.set(intellijBuildVersion)
    pluginName.set("sonarlint-intellij")
    updateSinceUntilBuild.set(false)
    plugins.set(listOf("java", "git4idea"))
}

tasks.runPluginVerifier {
    // Test oldest supported, and latest
    ideVersions.set(listOf("IC-2020.3.4", "IC-2021.3"))
    failureLevel.set(
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
                RunPluginVerifierTask.FailureLevel.MISSING_DEPENDENCIES,
                RunPluginVerifierTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
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
            languageVersion.set(JavaLanguageVersion.of(11))
        }
    }
    useJUnit()
    systemProperty("sonarlint.telemetry.disabled", "true")
}

val runIdeDirectory: String by project

tasks.runIde {
    systemProperty("sonarlint.telemetry.disabled", "true")
    if (project.hasProperty("runIdeDirectory")) {
        ideDir.set(File(runIdeDirectory))
    }
    maxHeapSize = "2g"
}

configurations {
    var sqplugins = create("sqplugins") { isTransitive = false }
    create("sqplugins_deps") {
        extendsFrom(sqplugins)
        isTransitive = true
    }
}

dependencies {
    implementation("org.sonarsource.sonarlint.core:sonarlint-core:$sonarlintCoreVersion")
    implementation("commons-lang:commons-lang:2.6")
    compileOnly("com.google.code.findbugs:jsr305:2.0.2")
    implementation("org.apache.httpcomponents.client5:httpclient5:5.1.2") {
        exclude(module = "slf4j-api")
    }
    implementation(project(":common"))
    runtimeOnly(project(":clion"))
    runtimeOnly(project(":rider"))
    runtimeOnly(project(":git"))
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation("org.mockito:mockito-core:4.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
    testImplementation("org.eclipse.jetty:jetty-server:$jettyVersion")
    testImplementation("org.eclipse.jetty:jetty-servlet:$jettyVersion")
    testImplementation("org.eclipse.jetty:jetty-proxy:$jettyVersion")
    "sqplugins"("org.sonarsource.java:sonar-java-plugin:7.13.0.29990")
    "sqplugins"("org.sonarsource.javascript:sonar-javascript-plugin:9.4.0.18205")
    "sqplugins"("org.sonarsource.php:sonar-php-plugin:3.23.1.8766")
    "sqplugins"("org.sonarsource.python:sonar-python-plugin:3.15.1.9817")
    "sqplugins"("org.sonarsource.kotlin:sonar-kotlin-plugin:2.9.0.1147")
    "sqplugins"("org.sonarsource.slang:sonar-ruby-plugin:1.10.0.3710")
    "sqplugins"("org.sonarsource.html:sonar-html-plugin:3.6.0.3106")
    "sqplugins"("org.sonarsource.xml:sonar-xml-plugin:2.5.0.3376")
    "sqplugins"("org.sonarsource.sonarlint.omnisharp:sonarlint-omnisharp-plugin:1.4.0.50839")
    if (artifactoryUsername.isNotEmpty() && artifactoryPassword.isNotEmpty()) {
        "sqplugins"("com.sonarsource.cpp:sonar-cfamily-plugin:6.35.0.50389")
        "sqplugins"("com.sonarsource.secrets:sonar-secrets-plugin:1.1.0.36766")
    }
    // workaround for light tests in 2020.3, might remove later
    testRuntimeOnly("org.jetbrains.kotlin:kotlin-reflect")
}

tasks {

    val downloadOmnisharpMonoZipFile by registering(Download::class) {
        src("https://repox.jfrog.io/artifactory/sonarsource/org/sonarsource/sonarlint/omnisharp/omnisharp-roslyn/$omnisharpVersion/omnisharp-roslyn-$omnisharpVersion-mono.zip")
        dest(File(buildDir, "omnisharp-$omnisharpVersion-mono.zip"))
        overwrite(false)
    }

    val downloadOmnisharpWinZipFile by registering(Download::class) {
        src("https://repox.jfrog.io/artifactory/sonarsource/org/sonarsource/sonarlint/omnisharp/omnisharp-roslyn/$omnisharpVersion/omnisharp-roslyn-$omnisharpVersion-net472.zip")
        dest(File(buildDir, "omnisharp-$omnisharpVersion-net472.zip"))
        overwrite(false)
    }

    val downloadOmnisharpNet6ZipFile by registering(Download::class) {
        src("https://repox.jfrog.io/artifactory/sonarsource/org/sonarsource/sonarlint/omnisharp/omnisharp-roslyn/$omnisharpVersion/omnisharp-roslyn-$omnisharpVersion-net6.zip")
        dest(File(buildDir, "omnisharp-$omnisharpVersion-net6.zip"))
        overwrite(false)
    }

    fun copyPlugins(destinationDir: File, pluginName: Property<String>) {
        copy {
            from(project.configurations.get("sqplugins"))
            into(file("$destinationDir/${pluginName.get()}/plugins"))
        }
    }

    fun copyOmnisharp(destinationDir: File, pluginName: Property<String>) {
        copy {
            from(zipTree(downloadOmnisharpMonoZipFile.get().dest))
            into(file("$destinationDir/${pluginName.get()}/omnisharp/mono"))
        }
        copy {
            from(zipTree(downloadOmnisharpWinZipFile.get().dest))
            into(file("$destinationDir/${pluginName.get()}/omnisharp/win"))
        }
        copy {
            from(zipTree(downloadOmnisharpNet6ZipFile.get().dest))
            into(file("$destinationDir/${pluginName.get()}/omnisharp/net6"))
        }
    }

    prepareSandbox {
        dependsOn(downloadOmnisharpMonoZipFile, downloadOmnisharpWinZipFile, downloadOmnisharpNet6ZipFile)
        doLast {
            copyPlugins(destinationDir, pluginName)
            copyOmnisharp(destinationDir, pluginName)
        }
    }

    prepareTestingSandbox {
        dependsOn(downloadOmnisharpMonoZipFile, downloadOmnisharpWinZipFile, downloadOmnisharpNet6ZipFile)
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
        classDirectories.setFrom(files("build/classes/java/main-instrumented"))
        reports {
            xml.required.set(true)
        }
    }
}

sonarqube {
    properties {
        property("sonar.projectName", "SonarLint for IntelliJ IDEA")
    }
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

artifactory {
    clientConfig.info.setBuildName("sonarlint-intellij")
    clientConfig.info.setBuildNumber(System.getenv("BUILD_BUILDID"))
    clientConfig.setIncludeEnvVars(true)
    clientConfig.setEnvVarsExcludePatterns("*password*,*PASSWORD*,*secret*,*MAVEN_CMD_LINE_ARGS*,sun.java.command,*token*,*TOKEN*,*LOGIN*,*login*,*key*,*KEY*,*PASSPHRASE*,*signing*")
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

