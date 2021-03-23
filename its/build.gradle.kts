import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.net.URL

plugins {
    id("org.jetbrains.intellij")
    id("com.github.hierynomus.license")
    kotlin("jvm")
}

group = "org.sonarsource.sonarlint.intellij.its"
description = "ITs for SonarLint IntelliJ"

val compileKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks
compileKotlin.kotlinOptions.jvmTarget = "1.8"

val compileTestKotlin: org.jetbrains.kotlin.gradle.tasks.KotlinCompile by tasks
compileTestKotlin.kotlinOptions.jvmTarget = "1.8"

repositories {
    jcenter()
    mavenLocal()
    mavenCentral()
    maven("https://repox.jfrog.io/repox/sonarsource")
    maven("https://jetbrains.bintray.com/intellij-third-party-dependencies")
}

val remoteRobotVersion = "0.10.0"
val fixturesVersion = "1.1.18"

dependencies {
    testImplementation("org.sonarsource.orchestrator:sonar-orchestrator:3.34.0.2692") {
        exclude(group = "org.slf4j", module = "log4j-over-slf4j")
    }
    testImplementation("org.sonarsource.sonarqube:sonar-ws:8.5.1.38104")
    testImplementation("com.intellij.remoterobot:remote-robot:$remoteRobotVersion")
    testImplementation("com.intellij.remoterobot:remote-fixtures:$fixturesVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
}

tasks.test {
    useJUnitPlatform()
}

license {
    mapping(
        mapOf(
            "kt" to "SLASHSTAR_STYLE"
        )
    )
    // exclude file from resources (workaround for https://github.com/hierynomus/license-gradle-plugin/issues/145)
    exclude("**.xml")
    strictCheck = true
}

tasks.downloadRobotServerPlugin {
    version = remoteRobotVersion
}

val ijVersion: String by project

intellij {
    version = if (project.hasProperty("ijVersion")) ijVersion else rootProject.intellij.version
    pluginName = "sonarlint-intellij"
    updateSinceUntilBuild = false
    if (!project.hasProperty("slPluginDirectory")) {
        setPlugins(rootProject)
    }
}

tasks.runIdeForUiTests {
    systemProperty("robot-server.port", "8082")
    systemProperty("sonarlint.telemetry.disabled", "true")
    systemProperty("jb.privacy.policy.text", "<!--999.999-->")
    systemProperty("jb.consents.confirmation.enabled", "false")
    jvmArgs = listOf("-Xmx1G")
}

open class RunIdeForUiTestAsyncTask : DefaultTask() {

    @org.gradle.api.tasks.Internal
    var execTaskFuture: Future<*>? = null

    @TaskAction
    fun startAsync() {
        val es = Executors.newSingleThreadExecutor()
        execTaskFuture = es.submit {
            project.tasks.runIdeForUiTests.get().exec()
        }
    }

    fun stop() {
        if (execTaskFuture != null) {
            println("Closing IDE")
            execTaskFuture?.cancel(true)
        }
    }
}

val runIdeForUiTestsAsync by tasks.register<RunIdeForUiTestAsyncTask>("runIdeForUiTestsAsync") {
    dependsOn(tasks.runIdeForUiTests.get().dependsOn)
    doFirst {
        if (project.hasProperty("slPluginDirectory")) {
            copy {
                from(project.property("slPluginDirectory"))
                into(tasks.runIdeForUiTests.get().pluginsDirectory)
            }
        }
    }
}

open class WaitRobotServerTask : DefaultTask() {
    var port = "8082"
    var totalTimeSeconds = 240
    var retryPeriodSeconds = 5

    @TaskAction
    fun waitService() {
        var remainingTime = totalTimeSeconds
        println("Waiting for robot server on port $port")
        while (remainingTime > 0) {
            try {
                URL("http://localhost:$port").openStream()
                println("Robot server is running!")
                return
            } catch (ignored: Exception) {
                Thread.sleep(retryPeriodSeconds * 1000L)
                remainingTime -= retryPeriodSeconds
            }
        }
        throw RuntimeException("Robot server is unreachable")
    }
}

val waitRobotServer by tasks.register<WaitRobotServerTask>("waitRobotServer") {
    mustRunAfter(runIdeForUiTestsAsync)
}

tasks.test {
    mustRunAfter(waitRobotServer)
}

val runIts by tasks.register("runIts") {
    dependsOn(runIdeForUiTestsAsync, waitRobotServer, tasks.test)
}

tasks.check {
    dependsOn(runIts)
}

val closeIde by tasks.register("closeIde") {
    doLast {
        runIdeForUiTestsAsync.stop()
    }
}

runIts.finalizedBy(closeIde)


