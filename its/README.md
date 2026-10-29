# Integration Tests (ITs)

UI integration tests for SonarQube for IntelliJ using [IntelliJ Remote Robot](https://github.com/JetBrains/intellij-ui-test-robot).

## CI coverage

Versions come from [`gradle.properties`](../gradle.properties): `minSupportedIdeVersion`, `latestStableIdeVersion`, `eapIdeVersion`.

### Pull requests and `master` pushes

All IT runs share the reusable workflow [`qa-its.yml`](../.github/workflows/qa-its.yml).

- **Pull requests:** [`Integration Tests PR`](../.github/workflows/integration-tests-pr.yml) runs the ITs only when the PR has the `e2e-all` label. It runs again when the PR is updated or the label is added. It is separate from `Build`, so it does not block `promote`.
- **Pushes to `master`, `branch-*` and `dogfood-*`:** `Build` runs the ITs against the plugin it deployed. They block `promote`.

Pull requests and pushes use a path-filtered matrix from [`.github/scripts/qa-matrix.py`](../.github/scripts/qa-matrix.py), using Python's standard library:

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
- IntelliJ Ultimate × 4 suites, CLion, and Rider against `eapIdeVersion` (Rider uses `eapRiderIdeVersion` when set)

Flavor jobs set `TEST_SUITE` to the matching JUnit tag so they do not discover IntelliJ-only classes (for example PLSQL on Rider, which ships the Database plugin). Failures notify Slack; they do not block PR promotion. PRs already cover latest IntelliJ (and CLion/Rider when those modules change). After this workflow exists on `master`, run it on demand from Actions → Integration Tests Weekly → Run workflow (`workflow_dispatch`). GitHub only lists that control once the file is on the default branch.

QA jobs run `./gradlew :its:check` with `TEST_SUITE` as JUnit `includeTags`. Standalone and flavor classes must stay in `src/test` with those tags.

**Merge order:** land this PR before any move of those classes to another source set (SLI-2914 / #1912). That follow-up must rebase onto this contract, keep the tags, and change `.github/actions/run-qa-its` to run the new Gradle task. Merging the two independently would make Standalone and flavor jobs select no tests.

### One-off runs against any IDE version

Actions → Integration Tests Weekly → Run workflow, with an `ide_version` input, runs only that IDE instead of the weekly matrix. Use it to reproduce a user report on their exact version or to try a new EAP build. You can run it from any branch.

- `ide_version`: `<CODE>-<version>` (`IC`, `IU`, `CL`, `RD`, `PY`, `PC`, `PS`, `GO`). The version is the one in the download.jetbrains.com tarball name, which Repox mirrors: for example `IC-2026.2.3`, or `IU-263.7307.44` / `RD-2026.3-EAP8-263.7307.48.Checked` for EAPs (EAP tarballs are named by build number). The [JetBrains releases API](https://data.services.jetbrains.com/products/releases?code=IIU&latest=true&type=eap) lists the current builds.
- `test_suite`: `default` runs the 4 core suites on `IC`/`IU` and the product's suite elsewhere. Pick one suite to narrow the run, or `PLSQL` on `IU`.

The matrix job rejects malformed versions and suites the product cannot run. `IC`, `IU`, `CL` and `RD` 2024 versions always run on the 2024.2 IDE embedded in the CI container (including 2024.2.x patch versions), so other 2024 versions of these products are rejected rather than silently testing 2024.2. A version that does not exist fails in the IDE download step with the download.jetbrains.com URL to check. These runs are named `Custom ITs - <ide_version> - <suite>` and do not notify Slack.

## Version bumps

```properties
minSupportedIdeVersion=2024.2
latestStableIdeVersion=2025.3.2   # bump when JetBrains ships a new stable
eapIdeVersion=2026.3-EAP          # must match the Repox artifact name
# Optional per-product overrides when patch numbers diverge
minRiderIdeVersion=2024.2.7
latestRiderIdeVersion=2025.3.2
eapRiderIdeVersion=2026.3-EAP
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
