val sonarlintCoreVersion: String by project
val riderBuildVersion: String by project

plugins {
    kotlin("jvm")
}

intellij {
    version = riderBuildVersion
}

dependencies {
    implementation(project(":common"))
    implementation("org.sonarsource.sonarlint.core:sonarlint-core:$sonarlintCoreVersion")
}

