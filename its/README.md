# Integration Tests (ITs)

UI integration tests for SonarLint using [IntelliJ Remote Robot](https://github.com/JetBrains/intellij-ui-test-robot).

## CI coverage

PR builds run **6 jobs** against three representative IDEs × two versions (from `gradle.properties`):

| IDE                             | Role                                                                                       |
|---------------------------------|--------------------------------------------------------------------------------------------|
| **IntelliJ IDEA Ultimate (IU)** | All IntelliJ-platform flows + Ultimate languages (IaC, Kotlin, XML, JS/TS, SQL, Go plugin) |
| **CLion (CL)**                  | C++ analysis                                                                               |
| **Rider (RD)**                  | C# analysis                                                                                |

Versions tested: `minSupportedIdeVersion` (2024.2, embedded in CI container) and `latestStableIdeVersion` (bump manually in `gradle.properties`).

**Not run in CI** (still available locally): PhpStorm, PyCharm, GoLand-specific flavor tests. These IDEs are treated as subsets of the IntelliJ platform for CI purposes.

**Weekly EAP** (Monday, `Integration Tests EAP` workflow): IU/CL/RD against `eapIdeVersion` from `gradle.properties`. Failures notify Slack; they do not block PR promotion.

## Version bumps

Edit [`gradle.properties`](../gradle.properties):

```properties
minSupportedIdeVersion=2024.2
latestStableIdeVersion=2025.3.2   # bump when JetBrains ships a new stable
eapIdeVersion=2026.1-EAP          # bump for weekly EAP runs
```

## Local run

1. Start IDE with robot server:

```bash
gradle :its:runIdeForUiTests -PijVersion=IU-2024.2
```

2. Run tests (separate terminal):

```bash
gradle :its:test -PijVersion=IU-2024.2
```

Filter by suite tag (optional):

```bash
TEST_SUITE=ConnectedAnalysisTests gradle :its:test -PijVersion=IU-2024.2
```

## Architecture notes

- Tests are **sequential** (single Remote Robot on port 8082).
- Connected-mode tests share one SonarQube Orchestrator via [`DeveloperOrchestratorUiTest`](src/test/kotlin/org/sonarlint/intellij/its/DeveloperOrchestratorUiTest.kt).
- Ansible and PLSQL tests use dedicated Orchestrator instances (Enterprise/profile-specific setup).
