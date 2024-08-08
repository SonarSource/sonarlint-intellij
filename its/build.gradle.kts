plugins {
    id("org.jetbrains.intellij.platform")
    kotlin("jvm")
}

group = "org.sonarsource.sonarlint.intellij.its"
description = "ITs for SonarLint IntelliJ"
val intellijBuildVersion: String by project

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
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        if (project.hasProperty("ijVersion")) {
            create(ijVersion)
        } else {
            intellijIdeaCommunity(intellijBuildVersion, useInstaller = false)
        }
        if (!project.hasProperty("slPluginDirectory")) {
            localPlugin(project(":"))
        }
    }
    testImplementation("org.sonarsource.orchestrator:sonar-orchestrator-junit5:4.2.0.542") {
        exclude(group = "org.slf4j", module = "log4j-over-slf4j")
    }
    testImplementation(libs.its.sonar.scala)
    testImplementation(libs.its.sonar.ws)
    testImplementation(libs.bundles.its.remote)
    testImplementation(libs.junit.api)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.engine)
    // Needed for https://github.com/gradle/gradle/issues/22333
    testRuntimeOnly(libs.junit.launcher)
}

tasks {
    test {
        useJUnitPlatform {
            val tag = System.getenv("TEST_SUITE")

            if (tag != null && (tag.equals("OpenInIdeTests") || tag.equals("ConnectedAnalysisTests")
                    || tag.equals("ConfigurationTests") || tag.equals("Standalone"))
            ) {
                includeTags(tag)
            }
        }
        testLogging.showStandardStreams = true
    }

    val runIdeDirectory: String by project

    val runIdeForUiTests by intellijPlatformTesting.runIde.registering {
        task {
            jvmArgumentProviders += CommandLineArgumentProvider {
                listOf(
                    "-Xmx1G",
                    "-Drobot-server.port=8082",
                    "-Dsonarlint.internal.sonarcloud.url=https://sc-staging.io",
                    "-Dsonarlint.internal.sonarcloud.websocket.url=wss://events-api.sc-staging.io/",
                    "-Dsonarlint.telemetry.disabled=true",
                    "-Dsonarlint.logs.verbose=true",
                    "-Didea.trust.all.projects=true",
                    "-Dide.show.tips.on.startup.default.value=false",
                    "-Djb.privacy.policy.text=<!--999.999-->",
                    "-Djb.consents.confirmation.enabled=false",
                    "-Deap.require.license=true"
                )
            }

            if (project.hasProperty("runIdeDirectory")) {
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf(
                        "-DideDir=$runIdeDirectory"
                    )
                }
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
            robotServerPlugin()
            localPlugin(rootProject.dependencies.project(":"))
        }
    }
}

license {
    // exclude file from resources (workaround for https://github.com/hierynomus/license-gradle-plugin/issues/145)
    exclude("**.xml")
}

val ijVersion: String by project

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "223.8214.6"
        }
        name = "sonarlint-intellij-its"
    }
    instrumentCode.set(false)
}
