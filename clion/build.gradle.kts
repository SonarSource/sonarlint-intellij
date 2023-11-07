val sonarlintCoreVersion: String by project
val clionBuildVersion: String by project

intellij {
    version.set(clionBuildVersion)
    plugins.set(listOf("com.intellij.clion", "com.intellij.cidr.base", "com.intellij.cidr.lang"))
}

dependencies {
    implementation(project(":common"))
    // Needed for https://github.com/gradle/gradle/issues/22333
    testRuntimeOnly(libs.junit.launcher)
    testRuntimeOnly(libs.junit.engine)
    testImplementation(libs.mockito.core)
    testImplementation(libs.junit.api)
}

tasks.test {
    useJUnitPlatform()
}
