import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.intellij.platform")
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.license)
    alias(libs.plugins.kotlin)
}

apply(from = "${rootProject.projectDir}/gradle/module-conventions.gradle")

sourceSets {
    create("integrationTest") {
        kotlin.srcDir("src/integrationTest/kotlin")
        kotlin.srcDir(
            if (itsDriverSdkVersion().startsWith("253.")) {
                "src/integrationTest253/kotlin"
            } else {
                "src/integrationTest242/kotlin"
            }
        )
        resources.srcDir("src/integrationTest/resources")
        compileClasspath += sourceSets["test"].output + sourceSets["test"].compileClasspath
        runtimeClasspath += sourceSets["test"].output + sourceSets["test"].runtimeClasspath
    }
}

val integrationTestImplementation by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}
val integrationTestRuntimeOnly by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}

// Repox serves the POM but Gradle's cached metadata omits the jar artifact for this legacy
// coordinate; screen recording is unused by our Standalone ITs.
configurations.matching { it.name.startsWith("integrationTest") }.configureEach {
    exclude(group = "com.github.stephenc.monte", module = "monte-screen-recorder")
}

// Pin driver SDK to the target IDE build (compile + runtime must match the IDE under test).
configurations.matching { it.name.startsWith("integrationTest") }.configureEach {
    val driverVersion = itsDriverSdkVersion()
    println("ITs: Using driver SDK $driverVersion (ijVersion=${project.findProperty("ijVersion") ?: "default"})")
    resolutionStrategy.force(
        "com.jetbrains.intellij.driver:driver-sdk:$driverVersion",
        "com.jetbrains.intellij.driver:driver-client:$driverVersion",
        "com.jetbrains.intellij.driver:driver-model:$driverVersion",
    )
}

// ITs require IDEs whose test-framework JARs introduce transitive deps that vary by environment
dependencyLocking {
    lockMode.set(LockMode.LENIENT)
}

val intellijBuildVersion: String by project
val ijVersion: String by project
val runIdeDirectory: String by project
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
            } else if (type == "IC" && version.startsWith("2025.3")) {
                println("ITs: WARNING: No *_HOME env var set, downloading IDEA $version from Repox (local development only)")
                intellijIdea(version) {
                    useCache = true
                }
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
            val ideaHome = System.getenv("IDEA_HOME")
            if (!ideaHome.isNullOrBlank() && File(ideaHome).exists()) {
                println("ITs: Using local IDE from IDEA_HOME=$ideaHome")
                local(ideaHome)
            } else {
                intellijIdeaCommunity(intellijBuildVersion)
            }
        }
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Bundled)
        testFramework(TestFrameworkType.Starter, configurationName = "integrationTestImplementation")
    }
    testImplementation(libs.its.orchestrator) {
        exclude(group = "org.slf4j", module = "log4j-over-slf4j")
    }
    testImplementation(libs.its.sonar.scala)
    testImplementation(libs.its.sonar.ws)
    testImplementation(libs.bundles.its.remote)
    // remote-robot → retrofit and sonar-ws still request okhttp 3.14.x; orchestrator brings okhttp-jvm 5.x.
    // Without this constraint both jars land on the ITS classpath with duplicate okhttp3 classes.
    constraints {
        testImplementation(libs.its.okhttp)
    }
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.junit.four)
    testRuntimeOnly(libs.junit.launcher)

    // ide-starter-squashed POM declares these but Gradle does not always resolve them onto
    // integrationTestRuntimeClasspath (legacy monte coordinate; IntelliJ-repackaged coroutines).
    integrationTestRuntimeOnly("com.intellij.platform:kotlinx-coroutines-core-jvm:1.8.0-intellij-9")
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

    named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileIntegrationTestKotlin") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    test {
        useJUnitPlatform {
            val tag = System.getenv("TEST_SUITE")
            if (tag != null && (tag == "OpenInIdeTests" || tag == "ConnectedAnalysisTests"
                    || tag == "ConfigurationTests")) {
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
    // Keep Orchestrator on SaaS: Edge returns HTTP 404 for api/search/versions on
    // sonarsource-releases, which Orchestrator uses to resolve SQ distributions.
    systemProperty("orchestrator.artifactory.url", "https://repox.jfrog.io/repox")
    val artifactoryToken = System.getenv("ARTIFACTORY_ACCESS_TOKEN")
        ?: System.getenv("ARTIFACTORY_PASSWORD")
    if (!artifactoryToken.isNullOrEmpty()) {
        systemProperty("orchestrator.artifactory.accessToken", artifactoryToken)
        systemProperty("orchestrator.artifactory.apiKey", artifactoryToken)
    }
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
            // UI-test IDE is not unit-test mode, so the bundled Python plugin refreshes
            // its PyPI package cache on startup. ITs never use that index; disable it so
            // the sandbox does not contact pypi.org.
            copy {
                from(layout.projectDirectory.dir("idea-config"))
                into(sandboxConfigDirectory)
            }
        }
    }

    plugins {
        robotServerPlugin(libs.versions.its.remote.get())
        // Only depend on building the root project plugin if slPluginDirectory is not provided
        if (!project.hasProperty("slPluginDirectory")) {
            localPlugin(rootProject.dependencies.project(":"))
        }
    }

    prepareSandboxTask {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
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

fun itsIdeProductCode(): String =
    if (project.hasProperty("ijVersion")) project.property("ijVersion").toString().substringBefore('-')
    else "IC"

fun itsIdeVersion(): String =
    if (project.hasProperty("ijVersion")) project.property("ijVersion").toString().substringAfter('-')
    else intellijBuildVersion

fun itsIdeBuildNumber(): String? = itsIdeBuildNumberFromHome(itsIdeHome())

fun itsTargetIdeHomeFromEnv(): String? {
    val type = if (project.hasProperty("ijVersion")) {
        project.property("ijVersion").toString().substringBefore('-')
    } else {
        "IC"
    }
    val envVar = when (type) {
        "IC", "IU" -> "IDEA_HOME"
        "CL" -> "CLION_HOME"
        "RD" -> "RIDER_HOME"
        "PY", "PC" -> "PYCHARM_HOME"
        "PS" -> "PHPSTORM_HOME"
        "GO" -> "GOLAND_HOME"
        else -> "IDEA_HOME"
    }
    return System.getenv(envVar)?.takeIf { path -> File(path).exists() }
}

fun itsDriverSdkVersion(): String {
    val buildNumber = itsIdeBuildNumberFromHome(itsTargetIdeHomeFromEnv() ?: itsIdeHome())
    return buildNumber ?: "242.20224.300"
}

fun itsIdeBuildNumberFromHome(ideHome: String?): String? {
    val productInfo = ideHome?.let { File(it, "product-info.json") } ?: return null
    if (!productInfo.isFile) return null
    return Regex(""""buildNumber"\s*:\s*"([^"]+)"""")
        .find(productInfo.readText())
        ?.groupValues
        ?.get(1)
}

fun cleanupStaleXvfbLock(displayNumber: Int) {
    val lockFile = File("/tmp/.X${displayNumber}-lock")
    if (!lockFile.exists()) return
    val process = ProcessBuilder("pgrep", "-f", "Xvfb.*:$displayNumber")
        .redirectErrorStream(true)
        .start()
    val running = process.inputStream.bufferedReader().readText().trim().isNotEmpty()
    process.waitFor()
    if (!running) {
        lockFile.delete()
        println("ITs: removed stale Xvfb lock /tmp/.X${displayNumber}-lock")
    }
}

fun itsIdeHome(): String? {
    val type = itsIdeProductCode()
    val envVarPath = when (type) {
        "IC", "IU" -> System.getenv("IDEA_HOME")
        "CL" -> System.getenv("CLION_HOME")
        "RD" -> System.getenv("RIDER_HOME")
        "PY", "PC" -> System.getenv("PYCHARM_HOME")
        "PS" -> System.getenv("PHPSTORM_HOME")
        "GO" -> System.getenv("GOLAND_HOME")
        else -> null
    }
    if (!envVarPath.isNullOrBlank() && File(envVarPath).exists()) {
        return envVarPath
    }
    val downloaded = rootProject.layout.projectDirectory.dir(".intellijPlatform/ides/${type}-${itsIdeVersion()}").asFile
    return downloaded.takeIf { it.exists() }?.absolutePath
}

val integrationTest by intellijPlatformTesting.testIdeUi.registering {
    task {
        val integrationTestSourceSet = sourceSets.getByName("integrationTest")
        testClassesDirs = integrationTestSourceSet.output.classesDirs
        classpath = integrationTestSourceSet.runtimeClasspath

        if (!project.hasProperty("slPluginDirectory")) {
            dependsOn(":buildPlugin")
        }
        systemProperty(
            "its.plugin.distributions.dir",
            if (project.hasProperty("slPluginDirectory")) {
                file(project.property("slPluginDirectory").toString()).absolutePath
            } else {
                rootProject.layout.buildDirectory.dir("distributions").get().asFile.absolutePath
            },
        )
        systemProperty("its.ide.product", itsIdeProductCode())
        systemProperty("its.ide.version", itsIdeVersion())
        itsIdeHome()?.let { systemProperty("its.ide.home", it) }
        itsIdeBuildNumber()?.let { systemProperty("its.ide.buildNumber", it) }
        systemProperty("its.projects.dir", layout.projectDirectory.dir("projects").asFile.absolutePath)
        systemProperty("its.idea.config.dir", layout.projectDirectory.dir("idea-config").asFile.absolutePath)

        useJUnitPlatform {
            val tag = System.getenv("TEST_SUITE")
            if (tag == "Standalone") {
                includeTags(tag)
            }
        }
        testLogging.showStandardStreams = true
    }
}

tasks.named<Test>("integrationTest") {
    doFirst {
        val pluginArchive = if (project.hasProperty("slPluginDirectory")) {
            val pluginDir = file(project.property("slPluginDirectory").toString())
            pluginDir.resolve("sonarlint-intellij.zip").takeIf { it.isFile }
                ?: pluginDir.walkTopDown()
                    .filter { file ->
                        file.isFile && file.name.startsWith("sonarlint-intellij-")
                            && file.extension == "zip" && !file.name.contains(".blockmap")
                    }
                    .maxByOrNull { it.lastModified() }
        } else {
            rootProject.layout.buildDirectory
                .file("distributions/sonarlint-intellij-${rootProject.version}.zip")
                .get()
                .asFile
                .takeIf { it.isFile }
        }
        checkNotNull(pluginArchive) { "Plugin archive not found for integration tests" }
        systemProperty("its.plugin.archive", pluginArchive.absolutePath)
    }
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    )
    // ide-starter transitives register xerces XML parser SPIs without the implementation jar on the classpath
    systemProperty(
        "javax.xml.parsers.SAXParserFactory",
        "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl",
    )
    systemProperty(
        "javax.xml.parsers.DocumentBuilderFactory",
        "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl",
    )
    // Starter skips its built-in xvfb-run wrapper whenever DISPLAY is set. On Wayland desktops DISPLAY is
    // often :0 but Gradle-spawned IDE processes cannot connect to that socket — unset it so Starter starts
    // its own virtual display. CI pre-starts Xvfb on :10; set ITS_USE_SYSTEM_DISPLAY=true to watch locally.
    doFirst {
        if (System.getenv("ITS_USE_SYSTEM_DISPLAY") != "true" && System.getenv("CI") != "true") {
            cleanupStaleXvfbLock(88)
            environment.remove("DISPLAY")
            environment.remove("WAYLAND_DISPLAY")
        }
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
    excludes(listOf("**/*.jar", "**/*.png", "**/README", "**.xml"))
    strictCheck = true
}
