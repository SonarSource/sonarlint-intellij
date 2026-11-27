# IDE E2E tests

These tests drive SonarQube for IntelliJ in a full IDE using [IntelliJ Remote Robot](https://github.com/JetBrains/intellij-ui-test-robot).

## CI coverage

Versions come from [`gradle.properties`](../gradle.properties): `minSupportedIdeVersion`, `latestStableIdeVersion`, `eapIdeVersion`.

### Pull requests and `master` pushes

All E2E runs share the reusable workflow [`qa-its.yml`](../.github/workflows/qa-its.yml).

- **Pull requests:** `Build` runs the `pr-minimal` matrix against the plugin it deployed: one IntelliJ smoke test (`SmokeIdea`), plus CLion and Rider when their files change. These jobs block `promote`.
- **Pull requests with the `e2e-all` label:** [`IDE E2E PR`](../.github/workflows/integration-tests-pr.yml) also runs the full `pr-all` matrix. It runs again when the PR is updated or the label is added. It is separate from `Build`, so it does not block `promote`, and it does not change the `pr-minimal` run in `Build`.
- **Pushes to `master`, `branch-*` and `dogfood-*`:** `Build` runs the `pr-all` matrix against the plugin it deployed. These jobs block `promote`.

Pull requests and pushes use a path-filtered matrix from [`.github/scripts/qa-matrix.py`](../.github/scripts/qa-matrix.py), using Python's standard library. The modes `pr-all` and `pr-minimal` differ only in the IntelliJ suites:

| When | Jobs |
|------|------|
| Any non-docs change | IntelliJ **latest**: `pr-all` runs 4 suites (`OpenInIdeTests`, `ConnectedAnalysisTests`, `ConfigurationTests`, `Standalone`). `pr-minimal` runs only `SmokeIdea` (open a Java file, check that the issue is found). |
| `clion/`, `clion-resharper/`, CLion plugin XMLs, or E2E or CI harness | CLion **latest** (`TEST_SUITE=CLion`) |
| `rider/`, Rider plugin XML / sources, or E2E or CI harness | Rider **latest** (`TEST_SUITE=Rider`) |
| Docs only (`*.md`, `docs/`, `HEADER`, …) | E2E tests skipped |

Harness means `e2e/`, `.github/`, Gradle files, or `mise.toml`. Changing those runs CLion and Rider as well as IntelliJ.

Pushes compare the complete pushed commit range. Manual workflow runs, newly created branches, and force-pushes whose previous SHA is not in the clone run all three IDEs. QA matrix regression tests run with `python3 .github/scripts/test_qa_matrix.py`.

Rider C# analysis needs a .NET 6 SDK on `PATH` so OmniSharp can start (`dotnet OmniSharp.dll`) and load the `net6.0` sample projects. CI installs that SDK on `RD-*` jobs. Local Rider E2E tests need the same.

### Weekly (Sundays 02:00 UTC)

One scheduled workflow, one plugin build:

- IntelliJ **min** × 4 suites, CLion min+latest, Rider min+latest, PhpStorm latest, one PyCharm (Professional), GoLand latest, IntelliJ Ultimate min (PLSQL)
- IntelliJ Ultimate × 4 suites, CLion, and Rider against `eapIdeVersion` (Rider and CLion use `eapRiderIdeVersion` and `eapCLionIdeVersion` when set)

Flavor jobs set `TEST_SUITE` to the matching JUnit tag so they do not discover IntelliJ-only classes (for example PLSQL on Rider, which ships the Database plugin). Failures notify Slack; they do not block PR promotion. PRs already run a smoke test on latest IntelliJ (and the CLion/Rider suites when those modules change). After this workflow exists on `master`, run it on demand from Actions → IDE E2E Weekly → Run workflow (`workflow_dispatch`). GitHub only lists that control once the file is on the default branch.

QA jobs run `./gradlew :its:check` with `TEST_SUITE` as JUnit `includeTags`. Standalone and flavor classes must stay in `src/test` with those tags.

**Merge order:** land this PR before any move of those classes to another source set (SLI-2914 / #1912). That follow-up must rebase onto this contract, keep the tags, and change `.github/actions/run-ide-e2e` to run the new Gradle task. Merging the two independently would make Standalone and flavor jobs select no tests.

### One-off runs against any IDE version

Actions → IDE E2E Weekly → Run workflow, with an `ide_version` input, runs only that IDE instead of the weekly matrix. Use it to reproduce a user report on their exact version or to try a new EAP build. You can run it from any branch.

- `ide_version`: `<CODE>-<version>` (`IC`, `IU`, `CL`, `RD`, `PY`, `PC`, `PS`, `GO`). The version is the one in the download.jetbrains.com tarball name, which Repox mirrors: for example `IC-2026.2.3`, or `IU-263.8311.30` / `RD-2026.3-EAP8-263.7307.48.Checked` for EAPs (EAP tarballs are named by build number). The [JetBrains releases API](https://data.services.jetbrains.com/products/releases?code=IIU&latest=true&type=eap) lists the current builds.
- `test_suite`: `default` runs the 4 core suites on `IC`/`IU` and the product's suite elsewhere. Pick one suite to narrow the run, or `PLSQL` on `IU`.

The matrix job rejects malformed versions and suites the product cannot run. `IC`, `IU`, `CL` and `RD` 2024 versions always run on the 2024.2 IDE embedded in the CI container (including 2024.2.x patch versions), so other 2024 versions of these products are rejected rather than silently testing 2024.2. A version that does not exist fails in the IDE download step with the download.jetbrains.com URL to check. These runs are named `Custom IDE E2E - <ide_version> - <suite>` and do not notify Slack.

## Version bumps

EAP builds expire even while their archives remain downloadable. If a recording shows
"EAP Build Expired", choose a current build from the JetBrains releases API and update
`eapIdeVersion` (or the product override) before retrying. If no usable EAP exists, temporarily
pin the release from the same platform line and document that fallback. A successful download
does not establish that an EAP can run.

```properties
minSupportedIdeVersion=2024.2
latestStableIdeVersion=2026.2.4   # bump when JetBrains ships a new stable
eapIdeVersion=263.8311.30         # EAPs are named by build number
# Per-product overrides when versions diverge (latestRiderIdeVersion and latestPyCharmIdeVersion also exist)
minRiderIdeVersion=2024.2.7
eapRiderIdeVersion=2026.3-EAP9-263.7307.100.Checked
eapCLionIdeVersion=2026.3.0.2 # temporary release fallback: the last CLion EAP has expired
```

Each value must match the Linux tarball name on download.jetbrains.com, which Repox mirrors as `jetbrains-download`. For EAPs, take it from the [JetBrains releases API](https://data.services.jetbrains.com/products/releases?code=IIU,CL,RD&latest=true&type=eap): for example `idea-263.8311.30.tar.gz` gives `263.8311.30`, and `JetBrains.Rider-2026.3-EAP9-263.7307.100.Checked.tar.gz` gives `2026.3-EAP9-263.7307.100.Checked`.

## Local run

The E2E sources live in `e2e/`. The Gradle project keeps the `:its` ID so scripts that use `:its:check` or exclude it with `-x :its:check` continue to work.

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
