allprojects {
    tasks.matching { it.name == "signArchives" }.configureEach {
        dependsOn(":composedJar")
    }
}