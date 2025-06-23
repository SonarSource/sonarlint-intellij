val clionBuildVersion: String by project
val clionHome: String? = System.getenv("CLION_HOME")

intellij {
    plugins.set(listOf("com.intellij.clion", "com.intellij.cidr.base", "com.intellij.cidr.lang", "com.intellij.clion.embedded"))
    if (!clionHome.isNullOrBlank()) {
        println("Using local installation of CLion: $clionHome")
        localPath.set(clionHome)
        localSourcesPath.set(clionHome)
    } else {
        println("No local installation of CLion found, using version $clionBuildVersion")
        version.set(clionBuildVersion)
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":clion-common"))
    testImplementation(libs.junit.api)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.engine)
}

tasks.test {
    useJUnitPlatform()
}
