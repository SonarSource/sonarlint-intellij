# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the plugin (no tests)
./gradlew buildPlugin

# Build with tests and checks
./gradlew check buildPlugin

# Run a single test class
./gradlew test --tests "org.sonarlint.intellij.SomeTestClass"

# Run a single test method
./gradlew test --tests "org.sonarlint.intellij.SomeTestClass.testMethodName"

# Run tests for a specific module
./gradlew :common:test
./gradlew :clion:test

# Run the plugin in a local IDE instance
./gradlew :runLocalIde

# Run against a specific IDE installation
./gradlew :runLocalIde -PrunIdeDirectory=/path/to/ide

# Plugin verification against IDEs
./gradlew :verifyPlugin
./gradlew :verifyPlugin -PverifierEnv=LATEST

# License header check (runs as part of check)
./gradlew license
```

## Architecture

This is a **multi-module IntelliJ Platform plugin** for SonarQube/SonarCloud integration (static code analysis). It uses Gradle Kotlin DSL with the IntelliJ Platform Gradle Plugin v2.11.0+.

### Module Structure

The root project is the main plugin. Submodules are **composed plugins** that add IDE-specific or language-specific capabilities:

- **`common/`** - Shared interfaces and extension point definitions used by all modules
- **`clion/`** - C/C++ analysis support (CLion-specific, depends on common)
- **`clion-resharper/`** - ReSharper C++ support (depends on common + clion)
- **`nodejs/`** - Node.js discovery for JavaScript/TypeScript analysis (depends on common)
- **`rider/`** - C#/.NET analysis support (depends on common + git)
- **`git/`** - Git VCS integration (depends on common)
- **`test-common/`** - Shared test fixtures (`testFixtures` configuration)
- **`its/`** - UI integration tests (JDK 21, uses Remote Robot framework)

All submodules apply `gradle/module-conventions.gradle` for shared build config.

### Plugin Extension Point System

The plugin defines custom extension points in `src/main/resources/META-INF/plugin.xml` that modules implement:

- `analysisConfiguration` (`AnalysisConfigurator`) - Language-specific analysis config (implemented by clion, rider)
- `fileExclusionContributor` (`FileExclusionContributor`) - File exclusion rules (implemented by clion)
- `filesContributor` (`FilesContributor`) - File selection logic (implemented by rider)
- `vcsProvider` (`VcsRepoProvider`) - VCS repository discovery (implemented by git, rider)
- `nodeJsProvider` (`NodeJsProvider`) - Node.js executable location (implemented by nodejs)

Extension point interfaces live in `common/src/main/java/org/sonarlint/intellij/common/`. Implementations are registered in per-module plugin XML files (e.g., `plugin-clion.xml`, `plugin-git.xml`).

### Key Architectural Patterns

**Backend Service (RPC):** Analysis runs in a separate Java process (SLOOP - SonarLint Out of Process) communicated with via RPC. `BackendService` (`src/.../core/BackendService.kt`) is the application-level service managing this.

**Analysis Flow:**
```
Action/Trigger -> AnalysisSubmitter -> BackendService (RPC) -> OnTheFlyFindingsHolder -> Editor Annotator + Tool Window
```

**Service Locator:** IntelliJ's `getService()` pattern is used throughout. Services are registered in `plugin.xml` at application, project, and module levels.

**Mixed Kotlin/Java:** New code is in Kotlin, legacy code in Java. Both target JVM 17 (JVM 21 for ITs only).

### Source Layout (root module)

Main source: `src/main/java/org/sonarlint/intellij/`

Key packages:
- `core/` - `BackendService`, binding manager, core lifecycle
- `analysis/` - Analysis submission and state tracking
- `finding/` - Issue/hotspot models and caching (`OnTheFlyFindingsHolder`)
- `editor/` - `SonarExternalAnnotator`, inline fixes, code vision
- `config/` - Global/project settings stores (`@State` services)
- `connected/` - SonarQube/SonarCloud connection and sync
- `trigger/` - Analysis trigger logic (on save, on change, pre-commit)
- `ui/` - Tool windows, finding trees, rule description panels
- `actions/` - User-triggered IDE actions (analyze, exclude, mark resolved)

### Dependencies

Dependency versions are centralized in `gradle/libs.versions.toml`. Key dependencies:
- `sonarlint-core` - SonarLint RPC backend
- Sonar analyzer plugins (Java, JS, Python, PHP, Go, C/C++, C#, etc.) bundled at runtime
- BouncyCastle for PGP signature verification
- Sentry for error reporting

### Testing

- Unit tests: `src/test/java/` using JUnit 5, Mockito, AssertJ
- Base classes from `test-common/`: `AbstractLightTests` (lightweight) and `AbstractHeavyTests` (full platform)
- `RunInEdtInterceptor` for JUnit 5 EDT handling
- UI integration tests in `its/` require a running IDE instance (`./gradlew :its:runIdeForUiTests &` then `./gradlew :its:check`)

### Build Notes

- External contributors: builds from release tags are guaranteed; development builds may require SonarSource Artifactory credentials for unreleased dependencies
- Without Artifactory credentials, C# enterprise plugin, OmniSharp, and CFamily signature downloads are skipped (see conditional blocks in `build.gradle.kts`)
- `prepareSandbox` extracts analyzer plugins, ESLint bridge, and SLOOP into the sandbox directory
- License headers are enforced via the `license` plugin (template in `HEADER` file)
