# SonarQube for IntelliJ — Claude Code Guide

## Prerequisites

- **Java 21** — install via your preferred tool (e.g. [mise](https://mise.jdx.dev/), sdkman, homebrew)
- **Gradle** — use the `./gradlew` wrapper; no separate installation needed

> CI uses `mise` (`mise.toml` defines the full toolchain including `gh`, `jfrog-cli`, and `maven`). For local development the wrapper and a JDK 21 are sufficient.

## Artifactory Configuration

All dependency resolution goes through Artifactory. There is no Maven Central fallback — builds fail without this configuration.

**Local development** — add to `~/.gradle/gradle.properties`:
```properties
artifactoryUrl=https://repox.jfrog.io/repox
artifactoryUsername=<your-username>
artifactoryPassword=<your-access-token>
```

> CI uses environment variables instead (`ARTIFACTORY_URL`, `ARTIFACTORY_ACCESS_USERNAME`, `ARTIFACTORY_ACCESS_TOKEN`). Environment variables take priority over `gradle.properties` if both are set.

## Architecture Overview

The plugin communicates with **SLOOP** (SonarLint Out-Of-Process) — the SLCORE analysis engine running as a separate child process.

**Communication model**
- **Plugin → SLOOP** (outbound): all calls go through `core/BackendService.kt`, an app-level IntelliJ service. It launches SLOOP via `SloopLauncher` and communicates over JSON-RPC using the `sonarlint-rpc-*` libraries.
- **SLOOP → Plugin** (inbound callbacks): SLOOP notifies the plugin through `SonarLintIntelliJClient.kt`, a singleton implementing `SonarLintRpcClientDelegate`.

**IntelliJ service layers**
Services are accessed via `SonarLintUtils.getService(Class)` (app-level) or `SonarLintUtils.getService(project, Class)` (project-level).
- App-level (one instance per IDE): `BackendService`, `SonarLintPlugin`
- Project-level (one instance per open project): `AnalysisSubmitter`, `ProjectBindingManager`, `AnalysisStatus`, `ReportTabManager`

**Where to look by change type**

| Change area | Start here |
|---|---|
| New backend RPC call | `core/BackendService.kt` |
| New SLOOP callback | `SonarLintIntelliJClient.kt` |
| Analysis trigger logic | `analysis/AnalysisSubmitter.kt`, `trigger/EditorOpenTrigger.kt` |
| Current file findings display | `ui/currentfile/CurrentFilePanel.kt` |
| Full report display | `ui/report/ReportPanel.kt` |
| SonarQube/SonarCloud connection | `core/ProjectBindingManager.java` |

## Module Structure

| Module | Purpose |
|---|---|
| `src/` | Main IntelliJ plugin code |
| `common/` | Shared plugin logic |
| `clion/`, `clion-resharper/` | CLion-specific code |
| `rider/` | Rider-specific code |
| `nodejs/` | Node.js language support |
| `git/` | Git integration |
| `test-common/` | Shared test utilities |
| `its/` | UI integration tests (CI-only) |

## Build and Validation

### Available commands

**Compile only** — fastest, catches syntax/type errors:
```bash
./gradlew compileKotlin compileTestKotlin
```

**Unit tests only** — excludes ITs and build artifact:
```bash
./gradlew -x :its:check -x :buildPlugin -x :buildPluginBlockmap -x :cyclonedxBom check
```

**Build plugin only** — no tests:
```bash
./gradlew -x test -x :its:check :buildPlugin
```

**Full check and build** (excludes ITs, which are CI-only):
```bash
./gradlew -x :its:check check buildPlugin
```

**Plugin compatibility verification** (local, minimal):
```bash
./gradlew :verifyPlugin -PverifierEnv=CI
```

### Running the plugin locally

To launch an IntelliJ instance with the plugin installed for manual validation:

```bash
./gradlew :runLocalIde
```

This downloads IntelliJ Community 2024.2 on first run. To use a local IDE installation instead:

```bash
./gradlew :runLocalIde -PrunIdeDirectory=<path-to-ide>
```

> On macOS, `<path-to-ide>` must point to the `Contents` directory inside the `.app` bundle (e.g. `"/Applications/IntelliJ IDEA.app/Contents"`).

The sandbox (instance files, JDK config) is stored under `build/sonarlint-test`. Running `./gradlew clean` will wipe it.

### Integration tests

ITs require a running display and `SONARCLOUD_IT_TOKEN`. Supported on **Windows and Linux only** — macOS is not supported due to native file dialog incompatibility with the UI test framework.

On headless Linux, `xvfb` is required. On Windows and Linux with a display, start the IDE with the plugin, wait for it to be ready, then run the tests:

```bash
./gradlew :its:runIdeForUiTests -PijVersion=IC-2024.2 &
./gradlew :its:check -PijVersion=IC-2024.2
```

### Optional: local IDE installation

Set these to skip downloading the IDE during builds (significantly faster first run):

```bash
IDEA_HOME=<path-to-intellij-installation>
CLION_HOME=<path-to-clion-installation>
RIDER_HOME=<path-to-rider-installation>
ULTIMATE_HOME=<path-to-intellij-ultimate-installation>
```

Alternatively, pass via Gradle property:
```bash
./gradlew :runLocalIde -PrunIdeDirectory=<path-to-ide>
```

## Dependency Management

Dependencies are declared in `gradle/libs.versions.toml`. Lock files (`gradle.lockfile` in each module) are committed and must stay in sync.

After adding, removing, or updating any dependency, regenerate all lock files:

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

Always commit the updated lock files together with the dependency change.
