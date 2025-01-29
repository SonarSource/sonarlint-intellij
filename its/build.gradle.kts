plugins {
    id("org.jetbrains.intellij")
    kotlin("jvm")
    jacoco
}

group = "org.sonarsource.sonarlint.intellij.its"
description = "ITs for SonarLint IntelliJ"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

val compileKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks
compileKotlin.kotlinOptions.jvmTarget = "17"

val compileTestKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks
compileTestKotlin.kotlinOptions.jvmTarget = "17"

repositories {
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
    mavenCentral()
}

dependencies {
    testImplementation("org.sonarsource.orchestrator:sonar-orchestrator-junit5:5.1.0.2254") {
        exclude(group = "org.slf4j", module = "log4j-over-slf4j")
    }
    testImplementation(libs.its.sonar.scala)
    testImplementation(libs.its.sonar.ws)
    testImplementation(libs.bundles.its.remote)
    testImplementation(libs.junit.api)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.engine)
}

tasks.test {
    useJUnitPlatform {
        val tag = System.getenv("TEST_SUITE")

        if (tag != null && (tag == "OpenInIdeTests" || tag == "ConnectedAnalysisTests"
                || tag == "ConfigurationTests" || tag == "Standalone")
        ) {
            includeTags(tag)
        }
    }
    testLogging.showStandardStreams = true

    // we need the coverage from Idea process, not from test task
    configure<JacocoTaskExtension> {
        isEnabled = false
    }
}

license {
    // exclude file from resources (workaround for https://github.com/hierynomus/license-gradle-plugin/issues/145)
    exclude("**.xml")
}

tasks.downloadRobotServerPlugin {
    version.set(libs.versions.its.remote)
}

val ijVersion: String by project

intellij {
    if (project.hasProperty("ijVersion")) {
        version.set(ijVersion)
    } else {
        if (rootProject.intellij.version.isPresent) {
            version.set(rootProject.intellij.version.get())
        } else {
            localPath.set(rootProject.intellij.localPath.get())
            localSourcesPath.set(rootProject.intellij.localSourcesPath.get())
        }
    }
    pluginName.set("sonarlint-intellij-its")
    updateSinceUntilBuild.set(false)
    if (!project.hasProperty("slPluginDirectory")) {
        plugins.set(listOf(rootProject))
    }
}

val runIdeDirectory: String by project

val uiTestsCoverageReport = tasks.register<JacocoReport>("uiTestsCoverageReport") {
    executionData(tasks.runIdeForUiTests.get())
    sourceSets(sourceSets.main.get())
    classDirectories.setFrom(files("../build/instrumented/instrumentCode"))

    reports {
        xml.required.set(true)
    }
}

jacoco {
    applyTo(tasks.runIdeForUiTests.get())
}



tasks.runIdeForUiTests {
    systemProperty("sonarlint.internal.sonarcloud.url", "https://sc-staging.io")
    systemProperty("sonarlint.internal.sonarcloud.websocket.url", "wss://events-api.sc-staging.io/")
    systemProperty("robot-server.port", "8082")
    systemProperty("sonarlint.telemetry.disabled", "true")
    systemProperty("sonarlint.logs.verbose", "true")
    systemProperty("idea.trust.all.projects", "true")
    systemProperty("ide.show.tips.on.startup.default.value", "false")
    systemProperty("jb.privacy.policy.text", "<!--999.999-->")
    systemProperty("jb.consents.confirmation.enabled", "false")
    systemProperty("eap.require.license", "true")
    jvmArgs = listOf("-Xmx1G")
    if (project.hasProperty("runIdeDirectory")) {
        ideDir.set(File(runIdeDirectory))
    }
    doFirst {
        if (project.hasProperty("slPluginDirectory")) {
            copy {
                from(project.property("slPluginDirectory"))
                into(pluginsDir.get())
            }
        }
    }
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
    finalizedBy(uiTestsCoverageReport)
}
