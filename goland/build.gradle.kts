val sonarlintCoreVersion: String by project
val golandBuildVersion: String by project

intellij {
    version.set(golandBuildVersion)
}

dependencies {
    implementation(project(":common"))
    implementation("org.sonarsource.sonarlint.core:sonarlint-core:$sonarlintCoreVersion")
}
