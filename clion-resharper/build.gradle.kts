val clionResharperBuildVersion: String by project
val resharperHome: String? = System.getenv("RESHARPER_HOME")

plugins {
    id("org.jetbrains.intellij.platform.module")
}

dependencies {
    intellijPlatform {
        if (!resharperHome.isNullOrBlank()) {
            println("Using local installation of Rider: $resharperHome")
            local(resharperHome)
        } else {
            println("No local installation of Rider found, using version $clionResharperBuildVersion")
            rider(clionResharperBuildVersion, useInstaller = false)
        }
    }
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
