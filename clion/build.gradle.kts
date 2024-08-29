val clionBuildVersion: String by project
val clionHome: String? = System.getenv("CLION_HOME")

intellij {
    plugins.set(listOf("com.intellij.clion", "com.intellij.cidr.base", "com.intellij.cidr.lang"))
    if (!clionHome.isNullOrBlank()) {
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
    testRuntimeOnly(libs.junit.engine)
}

tasks.test {
    useJUnitPlatform()
}
