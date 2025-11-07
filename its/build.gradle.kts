import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.intellij.platform")
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.license)
    alias(libs.plugins.kotlin)
    jacoco
}

apply(from = "${rootProject.projectDir}/gradle/module-conventions.gradle")

val intellijBuildVersion: String by project
val ijVersion: String by project
val runIdeDirectory: String by project
group = "org.sonarsource.sonarlint.intellij.its"
description = "ITs for SonarLint IntelliJ"

intellijPlatform {
    projectName = "sonarlint-intellij"
    buildSearchableOptions = false
}

dependencies {
    intellijPlatform {
        if (project.hasProperty("ijVersion")) {
            val type = ijVersion.split('-')[0]
            val version = ijVersion.split('-')[1]
            create(type, version) {
                useCache = true
            }
        } else {
            intellijIdeaCommunity(intellijBuildVersion)
        }
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Bundled)
    }
    testImplementation("org.sonarsource.orchestrator:sonar-orchestrator-junit5:5.1.0.2254") {
        exclude(group = "org.slf4j", module = "log4j-over-slf4j")
    }
    testImplementation(libs.its.sonar.scala)
    testImplementation(libs.its.sonar.ws)
    testImplementation(libs.bundles.its.remote)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.junit.four)
    testRuntimeOnly(libs.junit.launcher)
}

tasks {
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

    test {
        useJUnitPlatform {
            val tag = System.getenv("TEST_SUITE")
            if (tag != null && (tag == "OpenInIdeTests" || tag == "ConnectedAnalysisTests"
                    || tag == "ConfigurationTests" || tag == "Standalone")) {
                includeTags(tag)
            }
        }
        testLogging.showStandardStreams = true
        
        // Configure JaCoCo for test coverage
        configure<org.gradle.testing.jacoco.plugins.JacocoTaskExtension> {
            isIncludeNoLocationClasses = true
            excludes = listOf("jdk.internal.*")
        }
        
        // Finalize with coverage report generation
        finalizedBy(jacocoTestReport)
    }
    
    // Generate JaCoCo coverage report for UI tests
    jacocoTestReport {
        dependsOn(test)
        // Ensure root project's instrumented code is available (instrumentCode task is created by IntelliJ Platform Plugin)
        val rootInstrumentCode = project(":").tasks.findByName("instrumentCode")
        if (rootInstrumentCode != null) {
            mustRunAfter(rootInstrumentCode)
        } else {
            // Fallback to buildPlugin if instrumentCode doesn't exist
            mustRunAfter(project(":").tasks.named("buildPlugin"))
        }
        
        // Use the coverage data collected from the IDE process
        val execFile = project.layout.buildDirectory.file("jacoco/its.exec").get().asFile
        executionData.setFrom(
            files(execFile).filter { it.exists() }
        )
        
        // Only generate report if execution data exists
        onlyIf { execFile.exists() }
        
        // Report on the instrumented plugin code from the root project
        val instrumentedCodeDir = project(":").layout.buildDirectory.dir("instrumented/instrumentCode").get().asFile
        classDirectories.setFrom(
            files(instrumentedCodeDir).filter { it.exists() }
        )
        
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
}

val runIdeForUiTests by intellijPlatformTesting.runIde.registering {
    if (project.hasProperty("runIdeDirectory")) {
        localPath = file(runIdeDirectory)
    }

    task {
        val jacocoExecFile = project.layout.buildDirectory.file("jacoco/its.exec").get().asFile
        var agentJarPath: String? = null
        
        doFirst {
            jacocoExecFile.parentFile.mkdirs()
            
            // Resolve the JaCoCo agent JAR using the extension's agentJar property
            // This ensures we get the correct agent JAR with Premain-Class manifest
            val jacocoExtension = project.extensions.getByName("jacoco")
            val agentJarMethod = jacocoExtension.javaClass.getMethod("getAgentJar")
            val agentJarProvider = agentJarMethod.invoke(jacocoExtension) as Provider<*>
            val agentJarFile = agentJarProvider.get() as File
            
            if (!agentJarFile.exists()) {
                throw GradleException("JaCoCo agent JAR not found at: ${agentJarFile.absolutePath}")
            }
            
            agentJarPath = agentJarFile.absolutePath
        }
        
        jvmArgumentProviders += CommandLineArgumentProvider {
            // Use the agent JAR path resolved in doFirst
            val agentJar = agentJarPath ?: throw GradleException("JaCoCo agent JAR path not resolved. Ensure doFirst has run.")
            
            listOf(
                "-Xmx1G",
                "-javaagent:$agentJar=destfile=${jacocoExecFile.absolutePath}",
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
        }
    }

    plugins {
        robotServerPlugin("0.11.23")
        // Only depend on building the root project plugin if slPluginDirectory is not provided
        if (!project.hasProperty("slPluginDirectory")) {
            localPlugin(rootProject.dependencies.project(":"))
        }
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "231.9423.4"
        }
        name = "sonarlint-intellij-its"
    }
    // Enable code instrumentation for JaCoCo coverage collection
    instrumentCode.set(true)
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
