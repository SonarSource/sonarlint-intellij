# UI integration test strategy (SLI-2979)

## Goal

Keep a **small smoke layer** of UI integration tests (Remote Robot, real IDE frame) and move scenario coverage to **extension-driven light tests** (`AbstractSonarLintLightTests`). See [test-common/LIGHT_UI_TESTS.md](../test-common/LIGHT_UI_TESTS.md).

## Smoke (keep in `:its`)

| Area | Representative tests | Why UI IT remains |
| --- | --- | --- |
| Tool window stripe & tabs | `ConfigurationTests`, flavor-specific current-file checks | Physical tool window chrome |
| Settings navigation | Connected/binding flows in `ConfigurationTests` | Settings tree and search UI |
| Walkthrough once-per-user | `StandaloneIdeaTests.should_not_open_walkthrough_after_opened_once` | Frame-level welcome tool window |
| Focus on new code + rules | `StandaloneIdeaTests.should_exclude_rule_and_focus_on_new_code` | Multi-step UI with report tab |
| File exclusions via project settings | `StandaloneIdeaTests.should_exclude_file_and_analyze_file_and_no_issues_found` | Project Settings > File Exclusions UI (distinct from exclude-file menu action) |
| Helm/Kubernetes template in Findings tab | `StandaloneIdeaTests.should_analyze_ansible` | Real IDE + IaC analyzer on Helm chart project (no equivalent light UI test yet) |
| On-the-fly XML in Findings tab | `IdeaUltimateTests.should_analyze_xml` | End-to-end smoke on flavor IT fixture (light test `FindingsTabAnalysisLightTests` covers toolbar analyze on `file.xml` / `xml:S1778`) |

## Covered by light tests (same scenarios, UI IT not removed yet)

| UI IT scenario | Light test replacement |
| --- | --- |
| Exclude file menu action | `ExcludeFileActionLightTests` |
| Editor traffic light | `SonarLintTrafficLightActionLightTests` |
| On-the-fly XML issue in Findings tab | `FindingsTabAnalysisLightTests` (`xml:S1778` on `file.xml`; IT uses `sample-xml` / FIXME message) |
| Settings panels | `SonarLintConfigurableLightTests`, existing panel unit tests |
| Walkthrough factory content | `SonarLintWalkthroughToolWindowLightTests` |

## CI impact

Track `:its:test` and `:its:check` duration over time. Disabling or narrowing redundant standalone scenarios is follow-up work once light coverage is stable; light tests already run under `:test` (faster, no display).
