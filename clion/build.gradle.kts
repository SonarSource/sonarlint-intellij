plugins {
    id("org.jetbrains.intellij")
    java
    kotlin("jvm")
}

group = "org.sonarsource.sonarlint.intellij"
version = "4.15-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        apiVersion = "1.3"
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    testCompile("junit", "junit", "4.12")
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    version = "CL-2020.1.3"
    pluginName = "sonarlint-clion"
}
