# Light UI tests (extension-driven)

UI behavior should be exercised through the same **extension points**, **platform managers**, and **services** the product uses at runtime—not by constructing plugin UI classes directly in tests.

## Test pyramid

| Layer | When to use |
| --- | --- |
| **Unit** | Pure logic, models, renderers with mocks |
| **Light** (`AbstractSonarLintLightTests`) | Real SLOOP analysis, settings persistence, Swing trees, actions, notifications—headless IDE sandbox |
| **UI IT** (Remote Robot) | Thin smoke: real IDE frame, tool window stripe, settings navigation, flows that need a display |

Light tests are **not parallel-safe within one JVM** (shared application, SLOOP, storage).

## Headless tool windows

In the light sandbox, `plugin.xml` tool windows are **not** auto-registered. Page objects register and drive production factories:

| Piece | Location |
| --- | --- |
| Headless registration (`ensureMainSonarLintToolWindow`, `ensureWalkthroughToolWindow`) | [`HeadlessToolWindow.kt`](../src/test/java/org/sonarlint/intellij/ui/page/HeadlessToolWindow.kt) in `src/test` (not `test-common`) |
| Component tree lookup (`walkComponents`, `findComponents`) | [`ContainerExtensions.kt`](src/testFixtures/kotlin/org/sonarlint/intellij/fixtures/ContainerExtensions.kt) in `test-common` |

1. `SonarLintMainToolWindowPage.create(project)` or `SonarLintWalkthroughToolWindowPage.create(project)` — uses the helpers above
2. `@AfterEach` cleanup via `SonarLintUiTestContext.closeOpenEditorsAndSonarLintToolWindows()` (used by `AbstractSonarLintUILightTests` and standalone UI light tests)

The IDE Terminal works headless too: `TerminalToolWindowPage.create(project, testRootDisposable)` registers the Terminal plugin's tool window and shows each new tab, since the plugin starts a tab's command only once it is shown. Commands then really run, so only send ones that are safe to run, such as a fake CLI (see `AiIntegrationsTabLightTests`).

## Page objects

Light UI tests interact with the product through **page objects** (e.g. `SonarLintMainToolWindowPage` → `findings` → `findingsTree` / `issues`), not raw Swing containers or production panel types returned to tests. Subclass [`AbstractSonarLintUILightTests`](../src/test/java/org/sonarlint/intellij/AbstractSonarLintUILightTests.kt) to obtain a wired main tool window and automatic teardown.

Example assertion (see `FindingsTabAnalysisLightTests`):

```kotlin
val file = openFileFromTestData("src/file.xml")
awaitUntilAsserted {
    assertThat(trafficLightPageFor(file).issueCountLabelText).isNull()
    assertThat(mainToolWindow.findings.toolbar.analyzeCurrentFileButton.isEnabled).isTrue()
}
mainToolWindow.findings.toolbar.analyzeCurrentFileButton.click()
awaitUntilAsserted {
    assertThat(mainToolWindow.findings.issuesSummaryButtonText).isEqualTo("1 Issue")
    assertThat(mainToolWindow.findings.issues.single().ruleKey).isEqualTo("xml:S1778")
    assertThat(trafficLightPageFor(file).issueCountLabelText).isEqualTo("1")
}
```

Trigger analysis through the Findings toolbar page object (`toolbar.analyzeCurrentFileButton.click`), which invokes the production analyze action on the toolbar button. Wait until the button is enabled, then poll findings with [`awaitUntilAsserted`](../src/test/java/org/sonarlint/intellij/AbstractSonarLintUILightTests.kt)—not direct `AnalysisSubmitter` / `AnalysisStatus` calls.

Editor traffic light: build [`EditorTrafficLightPage`](../src/test/java/org/sonarlint/intellij/ui/page/EditorTrafficLightPage.kt) via [`trafficLightPageFor`](../src/test/java/org/sonarlint/intellij/AbstractSonarLintUILightTests.kt) so the widget is created through [`InspectionWidgetActionProvider`](../src/main/java/org/sonarlint/intellij/editor/SonarLintEditorWidgetActionProvider.kt) (not by instantiating `SonarLintTrafficLightAction` in tests). `issueCountLabelText` is null when there are no findings (green icon).

Deep component lookup uses [`ContainerExtensions.kt`](src/testFixtures/kotlin/org/sonarlint/intellij/fixtures/ContainerExtensions.kt) (`Component.walkComponents()`, `findComponents<T>()`).

The backend answers asynchronously, so wait for the UI with `awaitUntil { … }` from [`UiTestEdt.kt`](src/testFixtures/kotlin/org/sonarlint/intellij/fixtures/UiTestEdt.kt), then assert. To check notifications, call `recordNotifications()` from `AbstractSonarLintLightTests` before the action that notifies.

## Prefer extension points

| Surface | Entry point |
| --- | --- |
| Findings tool window | `SonarLintToolWindowFactory` |
| Walkthrough | `SonarLintWalkthroughToolWindowFactory` |
| Editor traffic light | `InspectionWidgetActionProvider` (`SonarLintEditorWidgetActionProvider`) |
| Notifications | `SonarLintProjectNotifications`, `NotificationGroup` EP, `Notifications.TOPIC` |
| Settings | `applicationConfigurable` / `projectConfigurable` EP (`sonarlint` ids in `plugin.xml`); page objects under [`ui/page/settings/`](../src/test/java/org/sonarlint/intellij/ui/page/settings/) |

## Settings page objects

Use [`openSqIdeSettings()`](../src/test/java/org/sonarlint/intellij/AbstractSonarLintUILightTests.kt) for **Tools → SonarQube for IDE** (global configurable) and [`openSqIdeProjectSettings()`](../src/test/java/org/sonarlint/intellij/AbstractSonarLintUILightTests.kt) for **Project Settings** (Findings toolbar configure action). Each opener returns a root page object with tab accessors. Project **File Exclusions** drives `AddEditExclusionDialog` through `enterFilePath`, `enterDirectoryPath`, or `enterGlobPattern`. Radios use `doClick()` so the matching field is enabled. Unit-test mode rejects `DialogWrapper.showAndGet()`, so `confirm()` reads the dialog value and writes it into the exclusions table. `save()` on the root page persists via `Configurable.apply()`.

Example (project file exclusion):

```kotlin
val settings = openSqIdeProjectSettings()
settings.fileExclusionsTab.addFileExclusion("src/main/java/foo/Bar.java")
settings.save()
assertThat(getProjectSettings().fileExclusions).containsExactly("FILE:src/main/java/foo/Bar.java")
```
