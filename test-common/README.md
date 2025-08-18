# Test Common Module

This module provides shared testing utilities and base classes that can be used across all test suites in the SonarLint IntelliJ project.

## Structure

The module uses Gradle's `java-test-fixtures` plugin to provide shared test components.

## Available Test Base Classes

### AbstractHeavyTests
Base class for heavy integration tests that require a full IntelliJ project setup.
- Extends `HeavyPlatformTestCase`
- Uses `RunInEdtInterceptor` for proper EDT handling
- Provides `@BeforeEach` and `@AfterEach` setup

### AbstractLightTests
Base class for lightweight unit tests.
- Extends `BasePlatformTestCase`
- Uses `RunInEdtInterceptor` for proper EDT handling
- Provides `@BeforeEach` and `@AfterEach` setup

### RunInEdtInterceptor
JUnit 5 extension that ensures all test methods run on the Event Dispatch Thread (EDT).

## Usage in Other Modules

To use these shared test utilities in another module:

1. Add the dependency in your module's `build.gradle.kts`:
```kotlin
dependencies {
    testImplementation(testFixtures(project(":test-common")))
    // other dependencies...
}
```

2. Extend the appropriate base class in your tests:
```kotlin
class MyTests : AbstractLightTests() {
    @Test
    fun `should do something`() {
        // your test code
    }
}
```
