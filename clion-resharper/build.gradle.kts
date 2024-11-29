val clionResharperBuildVersion: String by project
val resharperHome: String? = System.getenv("RESHARPER_HOME")

intellij {
    if (!resharperHome.isNullOrBlank()) {
        println("Using local installation of Rider: $resharperHome")
        localPath.set(resharperHome)
        localSourcesPath.set(resharperHome)
    } else {
        println("No local installation of Rider found, using version $clionResharperBuildVersion")
        version.set(clionResharperBuildVersion)
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":clion-common"))
    testImplementation(libs.junit.api)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.engine)
    compileOnly(libs.findbugs.jsr305)
}

tasks.test {
    useJUnitPlatform()
}
