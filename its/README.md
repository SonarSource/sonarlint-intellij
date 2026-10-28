# Integration Tests (ITs)

UI integration tests for SonarQube for IntelliJ using [IntelliJ Remote Robot](https://github.com/JetBrains/intellij-ui-test-robot).

## CI coverage

Minimum versions come from [`gradle.properties`](../gradle.properties). **Latest** and **EAP** are resolved at run time; see [IDE versions](#ide-versions).

### Pull requests and `master` pushes

Path-filtered matrix from [`.github/scripts/qa-matrix.py`](../.github/scripts/qa-matrix.py), using Python's standard library:

| When | Jobs |
|------|------|
| Any non-docs change | IntelliJ **latest** × 4 suites (`OpenInIdeTests`, `ConnectedAnalysisTests`, `ConfigurationTests`, `Standalone`) |
| `clion/`, `clion-resharper/`, CLion plugin XMLs, or IT/CI harness | CLion **latest** (`TEST_SUITE=CLion`) |
| `rider/`, Rider plugin XML / sources, or IT/CI harness | Rider **latest** (`TEST_SUITE=Rider`) |
| Docs only (`*.md`, `docs/`, `HEADER`, …) | ITs skipped |

Harness means `its/`, `.github/`, Gradle files, or `mise.toml`. Changing those runs CLion and Rider as well as IntelliJ.

Pushes compare the complete pushed commit range. Manual workflow runs, newly created branches, and force-pushes whose previous SHA is not in the clone run all three IDEs. QA matrix regression tests run with `python3 .github/scripts/test_qa_matrix.py`.

Rider C# analysis needs a .NET 6 SDK on `PATH` so OmniSharp can start (`dotnet OmniSharp.dll`) and load the `net6.0` sample projects. CI installs that SDK on `RD-*` jobs. Local Rider ITs need the same.

### Weekly (Sundays 02:00 UTC)

One scheduled workflow, one plugin build:

- IntelliJ **min** × 4 suites, CLion min+latest, Rider min+latest, PhpStorm latest, one PyCharm (Professional), GoLand latest, IntelliJ Ultimate min (PLSQL)
- IntelliJ Ultimate × 4 suites, CLion, and Rider against the newest **EAP** build (skipped for a product while no EAP newer than its stable release exists)

Flavor jobs set `TEST_SUITE` to the matching JUnit tag so they do not discover IntelliJ-only classes (for example PLSQL on Rider, which ships the Database plugin). Failures notify Slack; they do not block PR promotion. PRs already cover latest IntelliJ (and CLion/Rider when those modules change). After this workflow exists on `master`, run it on demand from Actions → Integration Tests Weekly → Run workflow (`workflow_dispatch`). GitHub only lists that control once the file is on the default branch.

QA jobs run `./gradlew :its:check` with `TEST_SUITE` as JUnit `includeTags`. Standalone and flavor classes must stay in `src/test` with those tags.

**Merge order:** land this PR before any move of those classes to another source set (SLI-2914 / #1912). That follow-up must rebase onto this contract, keep the tags, and change `.github/actions/run-qa-its` to run the new Gradle task. Merging the two independently would make Standalone and flavor jobs select no tests.

## IDE versions

`qa-matrix.py` asks the [JetBrains releases API](https://data.services.jetbrains.com/products/releases?code=IIU&latest=true&type=release) for the newest release and EAP of each product on every run. It derives each version from the Linux tarball name, so it matches the `jetbrains-download` path that [`download-ides.sh`](../.github/scripts/download-ides.sh) fetches from Repox (for example `IU-263.7307.44` or `RD-2026.3-EAP8-263.7307.48.Checked` for EAPs). If the API is unreachable or returns an unexpected tarball name, the matrix job fails instead of falling back to stale versions.

Only the minimum versions are pinned. Bump them when the supported range changes:

```properties
minSupportedIdeVersion=2024.2
# Optional per-product override when patch numbers diverge
minRiderIdeVersion=2024.2.7
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
