# Verifying dependencies

https://docs.gradle.org/8.9/userguide/dependency_verification.html#sec:dealing-verification-failure

Dependency verification consists of two different and complementary operations:

- checksum verification, which allows asserting the integrity of a dependency

- signature verification, which allows asserting the provenance of a dependency

In sonarlint-intellij, we only verify SHA-256 checksums, to ensure the integrity of an artifact.

## How it works

When a dependency is downloaded, its SHA-256 checksum is calculated and compared to the expected checksum. If the checksums don't match, the download is considered a failure.

The checksums are stored in `gradle/verification-metadata.xml`.

## How to update dependencies metadata

When updating a dependency, or adding a new one, a failure is likely to happen. In this case, you should run the following command:

`./gradlew --write-verification-metadata sha256`

It will add the new checksums to the metadata file. Depending on the context in which the dependency is used, you might have to run this verification on a different step, such as `./gradlew :buildPlugin --write-verification-metadata sha256`.

**You should always review the changes before committing them.**
