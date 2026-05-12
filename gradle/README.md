# Gradle Dependencies Management

This document provides guidelines for managing dependencies in our Gradle project.

## Version Catalog

We use Gradle's version catalog (`libs.versions.toml`) to centrally manage dependency versions. This helps maintain consistency across the project and simplifies dependency updates.

### Structure
- `[versions]`: Defines version numbers
- `[libraries]`: Defines dependencies
- `[bundles]`: Groups related dependencies
- `[plugins]`: Defines Gradle plugins

### Adding Dependencies

1. First, add the version to the `[versions]` section in `libs.versions.toml`:
   ```toml
   [versions]
   newDependency = "1.2.3"
   ```

2. Then, define the library:
   ```toml
   [libraries]
   new-dependency = { group = "com.example", name = "library", version.ref = "newDependency" }
   ```

3. Use in your build scripts:
   ```kotlin
   dependencies {
       implementation(libs.new.dependency)
   }
   ```

## Dependency Locking

We use [Gradle dependency locking](https://docs.gradle.org/current/userguide/dependency_locking.html) to ensure reproducible builds. Lock files (`gradle.lockfile`) are committed to the repository and record the exact resolved versions for every configuration.

### How It Works

When Gradle resolves dependencies, it checks that the resolved versions match the lock file. If a version was updated (e.g. via Renovate or a manual change to `libs.versions.toml`) without updating the lock file, the build fails.

### Updating Lock Files

After adding, removing, or updating a dependency, regenerate all lock files:

```bash
./gradlew --write-locks dependencies \
  :common:dependencies \
  :clion:dependencies \
  :clion-resharper:dependencies \
  :nodejs:dependencies \
  :git:dependencies \
  :rider:dependencies \
  :test-common:dependencies \
  :its:dependencies
```

> **Note**: Always commit the updated `gradle.lockfile` files together with the dependency changes.
