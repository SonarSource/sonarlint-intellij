# Building against EAP versions

The default build uses IntelliJ IDEA Community 2024.2. Select EAP versions explicitly.
Use Java 21 for Gradle, configure [Artifactory credentials](../AGENTS.md#artifactory-configuration),
and ensure the `jetbrains-intellij-snapshots` mirror is available.

## Build

Use numeric EAP build numbers for IDEA and CLion. Modern IDEA EAPs require `IU`.
This example selects EAP SDKs for the plugin, shared modules, and Node.js support:

```bash
IDEA_EAP=263.7031.23

env -u IDEA_HOME -u ULTIMATE_HOME -u CLION_HOME -u RIDER_HOME \
  ./gradlew classes testClasses :buildPlugin \
  -PintellijBuildType=IU -PintellijBuildVersion="$IDEA_EAP" \
  -PintellijUltimateBuildVersion="$IDEA_EAP"
```

Version selectors are independent. For both CLion modules, append `-PclionBuildVersion=263.7031.19`.
Rider uses a Maven SDK snapshot version: append `-PriderBuildVersion=2026.3-EAP6-SNAPSHOT`.

## Run and debug

Replace the build tasks above with `:runLocalIde`, keeping the same flags and environment.
Run that task in debug mode from IntelliJ's Gradle tool window to use breakpoints.

To launch an installed IDE, append `-PrunIdeDirectory="/path/to/IDE"`; this changes the running IDE, not the compilation SDKs.
`IDEA_HOME`, `ULTIMATE_HOME`, `CLION_HOME`, and `RIDER_HOME` override the corresponding compilation SDK version properties.
On macOS, local paths must point to `.app/Contents`. Use a runtime supported by the EAP.
The sandbox is in `build/sonarlint-test`.

## Current compatibility limits

- IDEA `263.7031.23` resolves dependencies but fails compilation at `GeneratedSourcesFilter.isGeneratedSourceByAnyFilter`.
- CLion `263.7031.19` removes the `com.intellij.cidr.lang` / `com.intellij.cidr.base` plugins and the `OCPsiFile` API, requiring a separate migration.

Successful EAP packaging and sandbox launch remain unvalidated.

## Dependency locks

Try committed locks first. If EAP dependencies conflict, run the [lock update command](../gradle/README.md#updating-lock-files)
in a disposable checkout with the same EAP flags and SDK environment. Do not commit experimental EAP locks.
