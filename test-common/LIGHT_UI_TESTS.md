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

## Page objects

Light UI tests interact with the product through **page objects** (e.g. `SonarLintMainToolWindowPage` → `findings` → `findingsTree` / `issues`), not raw Swing containers or production panel types returned to tests. Subclass [`AbstractSonarLintUILightTests`](../src/test/java/org/sonarlint/intellij/AbstractSonarLintUILightTests.kt) to obtain a wired main tool window and automatic teardown.

Example assertion (see `FindingsTabAnalysisLightTests`):

```kotlin
val file = openFileFromTestData("src/file.xml")
awaitUntilAsserted {
    assertThat(mainToolWindow.findings.toolbar.analyzeCurrentFileButton.isEnabled).isTrue()
}
mainToolWindow.findings.toolbar.analyzeCurrentFileButton.click(file)
awaitUntilAsserted {
    assertThat(mainToolWindow.findings.issuesSummaryButtonText).isEqualTo("1 Issue")
    assertThat(mainToolWindow.findings.issues.single().ruleKey).isEqualTo("xml:S1778")
}
```

Trigger analysis through the Findings toolbar page object (`toolbar.analyzeCurrentFileButton.click`), which invokes the production analyze action on the toolbar button. Wait until the button is enabled, then poll findings with [`awaitUntilAsserted`](../src/test/java/org/sonarlint/intellij/AbstractSonarLintUILightTests.kt)—not direct `AnalysisSubmitter` / `AnalysisStatus` calls.

Deep component lookup uses [`ContainerExtensions.kt`](src/testFixtures/kotlin/org/sonarlint/intellij/fixtures/ContainerExtensions.kt) (`Component.walkComponents()`, `findComponents<T>()`).

## Prefer extension points

| Surface | Entry point |
| --- | --- |
| Findings tool window | `SonarLintToolWindowFactory` |
| Walkthrough | `SonarLintWalkthroughToolWindowFactory` |
| Editor traffic light | `InspectionWidgetActionProvider` (`SonarLintEditorWidgetActionProvider`); page object [`EditorTrafficLightPage`](../src/test/java/org/sonarlint/intellij/ui/page/EditorTrafficLightPage.kt) |
| Notifications | `SonarLintProjectNotifications`, `NotificationGroup` EP, `Notifications.TOPIC` |
| Settings | `applicationConfigurable` / `projectConfigurable` EP (`sonarlint` ids in `plugin.xml`) |
