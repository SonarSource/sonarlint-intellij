val clionBuildVersion: String by project
val clionHome: String? = System.getenv("CLION_HOME")

intellij {
    plugins.set(listOf("com.intellij.clion", "com.intellij.cidr.base", "com.intellij.cidr.lang"))
    if (clionHome != null) {
        localPath.set(clionHome)
        localSourcesPath.set(clionHome)
    } else {
        version.set(clionBuildVersion)
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":clion-common"))
    testImplementation(libs.junit.api)
    testImplementation(libs.mockito.core)
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    // Needed for https://github.com/gradle/gradle/issues/22333
    testRuntimeOnly(libs.junit.launcher)
    testRuntimeOnly(libs.junit.engine)
}

tasks.test {
    useJUnitPlatform()
}
