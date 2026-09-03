# Integration Tests (ITs)

UI integration tests for SonarQube for IntelliJ using [IntelliJ Remote Robot](https://github.com/JetBrains/intellij-ui-test-robot).

## CI coverage

Versions come from [`gradle.properties`](../gradle.properties): `minSupportedIdeVersion`, `latestStableIdeVersion`, `eapIdeVersion`.

### Pull requests and `master` pushes

Path-filtered matrix from [`.github/scripts/qa-matrix.sh`](../.github/scripts/qa-matrix.sh):

| When | Jobs |
|------|------|
| Any non-docs change | IntelliJ **latest** × 4 suites (`OpenInIdeTests`, `ConnectedAnalysisTests`, `ConfigurationTests`, `Standalone`) |
| `clion/`, `clion-resharper/`, CLion plugin XMLs, or IT/CI harness | CLion **latest** (`TEST_SUITE=CLion`) |
| `rider/`, Rider plugin XML / sources, or IT/CI harness | Rider **latest** (`TEST_SUITE=Rider`) |
| Docs only (`*.md`, `docs/`, `HEADER`, …) | ITs skipped |

Harness means `its/`, `.github/`, Gradle files, or `mise.toml`. Changing those runs CLion and Rider as well as IntelliJ.

Rider C# analysis needs a .NET 6 SDK on `PATH` so OmniSharp can start (`dotnet OmniSharp.dll`) and load the `net6.0` sample projects. CI installs that SDK on `RD-*` jobs. Local Rider ITs need the same.

### Weekly (Sundays 02:00 UTC)

One scheduled workflow, one plugin build:

- IntelliJ **min** × 4 suites, CLion min+latest, Rider min+latest, PhpStorm latest, one PyCharm (Professional), GoLand latest, IntelliJ Ultimate min (PLSQL)
- IntelliJ Ultimate × 4 suites, CLion, and Rider against `eapIdeVersion`

Flavor jobs set `TEST_SUITE` to the matching JUnit tag so they do not discover IntelliJ-only classes (for example PLSQL on Rider, which ships the Database plugin). Failures notify Slack; they do not block PR promotion. PRs already cover latest IntelliJ (and CLion/Rider when those modules change).

## Version bumps

```properties
minSupportedIdeVersion=2024.2
latestStableIdeVersion=2025.3.2   # bump when JetBrains ships a new stable
eapIdeVersion=2026.1-EAP          # must match the Repox artifact name
# Optional per-product overrides when patch numbers diverge
minRiderIdeVersion=2024.2.7
latestRiderIdeVersion=2025.3.2
latestPyCharmIdeVersion=2025.3.2.1
```

## Local run

1. Start an IDE with the robot server:

```bash
./gradlew :its:runIdeForUiTests -PijVersion=IC-2025.3.2
```

2. In another terminal:

```bash
./gradlew :its:check -PijVersion=IC-2025.3.2
```

Filter by suite tag:

```bash
TEST_SUITE=ConnectedAnalysisTests ./gradlew :its:test -PijVersion=IC-2025.3.2
```

Core suites are gated with `isIntelliJIdea()` so they run on Community, Ultimate, and the 2025.3+ unified IntelliJ distribution.

Rider (`TEST_SUITE=Rider`, `-PijVersion=RD-…`) requires a .NET 6 SDK on `PATH` (`dotnet --info`). The sample projects target `net6.0` to match the embedded OmniSharp host.
