val clionResharperBuildVersion: String by project
val resharperHome: String? = System.getenv("RESHARPER_HOME")

intellij {
    if (!resharperHome.isNullOrBlank()) {
        localPath.set(resharperHome)
        localSourcesPath.set(resharperHome)
    } else {
        version.set(clionResharperBuildVersion)
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
    compileOnly(libs.findbugs.jsr305)
}

tasks.test {
    useJUnitPlatform()
}
