val riderBuildVersion: String by project
val riderHome: String? = System.getenv("RIDER_HOME")

plugins {
    id("org.jetbrains.intellij.platform.module")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.cyclonedx)
    alias(libs.plugins.license)
}

apply(from = "${rootProject.projectDir}/gradle/module-conventions.gradle")

dependencies {
    intellijPlatform {
        if (!riderHome.isNullOrBlank()) {
            println("Using local installation of Rider: $riderHome")
            local(riderHome)
        } else {
            println("No local installation of Rider found, using version $riderBuildVersion")
            rider(riderBuildVersion) { useInstaller = false }
        }
        pluginComposedModule(implementation(project(":common")))
        pluginComposedModule(implementation(project(":git")))
        bundledPlugins("Git4Idea")
    }
    compileOnly(libs.findbugs.jsr305)
}
