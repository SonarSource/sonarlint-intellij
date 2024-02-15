plugins {
    id("org.jetbrains.intellij")
    kotlin("jvm")
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

val remoteRobotVersion = "0.11.19"

dependencies {
    testImplementation("org.sonarsource.orchestrator:sonar-orchestrator-junit5:4.2.0.542") {
        exclude(group = "org.slf4j", module = "log4j-over-slf4j")
    }
    testImplementation("org.sonarsource.slang:sonar-scala-plugin:1.8.3.2219")
    testImplementation("org.sonarsource.sonarqube:sonar-ws:8.5.1.38104")
    testImplementation("com.intellij.remoterobot:remote-robot:$remoteRobotVersion")
    testImplementation("com.intellij.remoterobot:remote-fixtures:$remoteRobotVersion")
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    // Needed for https://github.com/gradle/gradle/issues/22333
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.assertj.core)
}

tasks.test {
    useJUnitPlatform()
    testLogging.showStandardStreams = true
}

license {
    // exclude file from resources (workaround for https://github.com/hierynomus/license-gradle-plugin/issues/145)
    exclude("**.xml")
}

tasks.downloadRobotServerPlugin {
    version.set(remoteRobotVersion)
}

val ijVersion: String by project

intellij {
    version.set(if (project.hasProperty("ijVersion")) ijVersion else rootProject.intellij.version.get())
    pluginName.set("sonarlint-intellij-its")
    updateSinceUntilBuild.set(false)
    if (!project.hasProperty("slPluginDirectory")) {
        plugins.set(listOf(rootProject))
    }
    instrumentCode.set(false)
}

val runIdeDirectory: String by project

tasks.runIdeForUiTests {
    systemProperty("sonarlint.internal.sonarcloud.url", "https://sc-staging.io")
    systemProperty("sonarlint.internal.sonarcloud.websocket.url", "wss://events-api.sc-staging.io/")
    systemProperty("robot-server.port", "8082")
    systemProperty("sonarlint.telemetry.disabled", "true")
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
}
