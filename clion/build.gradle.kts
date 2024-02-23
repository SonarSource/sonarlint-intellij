val clionBuildVersion: String by project

intellij {
    version.set(clionBuildVersion)
    plugins.set(listOf("com.intellij.clion", "com.intellij.cidr.base", "com.intellij.cidr.lang"))
}

dependencies {
    implementation(project(":common"))
    implementation(libs.sonarlint.core)
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    // Needed for https://github.com/gradle/gradle/issues/22333
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.mockito.core)
}

tasks.test {
    useJUnitPlatform()
}
