plugins {
    id("java")
}

dependencies {
  implementation("org.codehaus.sonar:sonar-channel:4.2") {
      exclude(module = "slf4j-api")
  }
  testImplementation("org.assertj:assertj-core:3.16.1")
  testImplementation("org.mockito:mockito-core:2.19.0")
}
