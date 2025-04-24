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


## Dependency Verification

For more details, refer to the [Gradle Dependency Verification Guide](https://docs.gradle.org/8.9/userguide/dependency_verification.html#sec:dealing-verification-failure).

Dependency verification ensures the integrity and provenance of dependencies through two key operations:

- **Checksum verification**: Confirms the integrity of a dependency.
- **Signature verification**: Validates the origin of a dependency.

In the `sonarlint-intellij` project, only **SHA-256 checksums** are verified to ensure artifact integrity.

### How It Works

When a dependency is downloaded, its SHA-256 checksum is calculated and compared to the expected value. If the checksums do not match, the download fails.  
The expected checksums are stored in the `gradle/verification-metadata.xml` file.

### Updating Dependency Metadata

When adding or updating a dependency, verification failures may occur. To resolve this, run the following command:

```bash
./gradlew --write-verification-metadata sha256
```

This command updates the metadata file with the new checksums. Depending on the build context, you might need to run a specific task, such as:

```bash
./gradlew :buildPlugin --write-verification-metadata sha256
```

> **Note**: Always review the changes to the metadata file before committing them.

### Cleaning Up Dependency Checksums

When new checksums are generated, older versions remain in the metadata file. To clean up outdated entries, follow these steps:

1. Review the newly added checksums.
2. Manually remove outdated entries.
3. Verify that the cleanup is complete.

The checksum file is ordered chronologically per dependency, making it easier to identify and remove old entries.

> **Tip**: Double-check removed entries to ensure you are not deleting checksums for active dependencies.

In case there are too many old entries, this is a more direct way of cleaning up the file:

1. Back up the current `verification-metadata.xml`
2. Generate a fresh verification file using `./gradlew --write-verification-metadata sha256`
3. Compare the files and identify the changes are correct
