val riderBuildVersion: String by project
val riderHome: String? = System.getenv("RIDER_HOME")

plugins {
    kotlin("jvm")
}

intellij {
    if (!riderHome.isNullOrBlank()) {
        println("Using local installation of IntelliJ IDEA: $riderHome")
        localPath.set(riderHome)
        localSourcesPath.set(riderHome)
    } else {
        println("No local installation of IntelliJ IDEA found, using version $riderBuildVersion")
        version.set(riderBuildVersion)
    }
    plugins.set(listOf("Git4Idea"))
}

dependencies {
    implementation(project(":common"))
    implementation(project(":git"))
    compileOnly(libs.findbugs.jsr305)
}
