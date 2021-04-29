val sonarlintCoreVersion: String by project
val clionBuildVersion: String by project

intellij {
    version.set(clionBuildVersion)
}

dependencies {
    implementation(project(":common"))
    implementation("org.sonarsource.sonarlint.core:sonarlint-core:$sonarlintCoreVersion")
    testImplementation(platform("org.junit:junit-bom:5.7.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:3.10.0")
}

tasks.test {
    useJUnitPlatform()
}
