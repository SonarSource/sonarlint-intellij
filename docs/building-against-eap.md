# Building against EAP versions

The default build stays on IntelliJ IDEA Community 2024.2. Select an EAP explicitly when testing new platform APIs.

## Prerequisites

Use Java 21 for Gradle and configure [Artifactory credentials](../AGENTS.md#artifactory-configuration).
EAP Maven artifacts require the `jetbrains-intellij-snapshots` Artifactory mirror of
`https://www.jetbrains.com/intellij-repository/snapshots`, available through the configured SaaS or Edge endpoint.
IDE installers continue to use the existing `jetbrains-download` mirror.

## Build with downloaded SDKs

Use exact numeric EAP build numbers from the JetBrains download pages for IDEA and CLion.
Rider uses the Maven SDK archive, whose published version is an EAP snapshot such as `2026.3-EAP6-SNAPSHOT`.
Modern unified IntelliJ IDEA EAPs use `IU`; the default `IC` selects Community.

The following are illustrative build numbers, **not validated successful builds**. Replace them with the EAPs you need:

```bash
IDEA_EAP=263.7031.23
CLION_EAP=263.7031.19
RIDER_EAP=2026.3-EAP6-SNAPSHOT

env -u IDEA_HOME -u ULTIMATE_HOME -u CLION_HOME -u RIDER_HOME \
  ./gradlew classes testClasses :buildPlugin \
  -PintellijBuildType=IU -PintellijBuildVersion="$IDEA_EAP" \
  -PintellijUltimateBuildVersion="$IDEA_EAP" \
  -PclionBuildVersion="$CLION_EAP" -PriderBuildVersion="$RIDER_EAP"
```

`intellijBuildVersion` selects IDEA for the plugin and shared modules; `intellijUltimateBuildVersion` selects the Node.js SDK.
`clionBuildVersion` selects both CLion modules, and `riderBuildVersion` selects Rider.
These properties are independent: keep products on the stable baseline when they are outside your investigation.

## Run and debug

Launch an IDEA sandbox with the same build flags used above:

```bash
env -u IDEA_HOME -u ULTIMATE_HOME -u CLION_HOME -u RIDER_HOME \
  ./gradlew :runLocalIde \
  -PintellijBuildType=IU -PintellijBuildVersion="$IDEA_EAP" \
  -PintellijUltimateBuildVersion="$IDEA_EAP" \
  -PclionBuildVersion="$CLION_EAP" -PriderBuildVersion="$RIDER_EAP"
```

To launch an installed IDEA, CLion, or Rider EAP, append `-PrunIdeDirectory="/path/to/IDE"`.
This selects the running IDE; compilation SDKs still follow the build properties or local SDK environment variables.
`IDEA_HOME`, `ULTIMATE_HOME`, `CLION_HOME`, and `RIDER_HOME` override the corresponding downloaded compilation SDKs when set.
On macOS, local paths must point to the `.app/Contents` directory.

For breakpoints, run `runLocalIde` in debug mode from IntelliJ's Gradle tool window, with the same arguments and environment.
The sandbox is in `build/sonarlint-test`. Use the IDE's bundled runtime (or a compatible runtime required by that EAP);
the project's compilation toolchain remains Java 21. New platform API, Kotlin, or JDK incompatibilities need separate investigation.

## Dependency locks

Try the committed locks first. If an EAP's transitive dependencies conflict with them, use a disposable checkout and run the
[all-module lock update command](../gradle/README.md#updating-lock-files), appending exactly the same EAP flags and keeping the same
SDK environment as the build. Retry the build in that checkout and do not commit experimental EAP lock files.
