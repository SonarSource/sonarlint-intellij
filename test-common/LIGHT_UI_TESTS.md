# Light UI tests (extension-driven)

UI behavior should be exercised through the same **extension points**, **platform managers**, and **services** the product uses at runtime—not by constructing plugin UI classes directly in tests.

## Test pyramid

| Layer | When to use |
| --- | --- |
| **Unit** | Pure logic, models, renderers with mocks |
| **Light** (`AbstractSonarLintLightTests`) | Real SLOOP analysis, settings persistence, Swing trees, actions, notifications—headless IDE sandbox |
| **UI IT** (Remote Robot) | Thin smoke: real IDE frame, tool window stripe, settings navigation, flows that need a display |

Light tests are **not parallel-safe within one JVM** (shared application, SLOOP, storage).

## Headless seams

In the light sandbox, `plugin.xml` tool windows are **not** auto-registered. Use [HeadlessToolWindowSupport](src/testFixtures/kotlin/org/sonarlint/intellij/fixtures/HeadlessToolWindowSupport.kt):

1. `registerToolWindowIfAbsent` with the production `ToolWindowFactory`
2. `ensureFactoryContentCreated` → `createToolWindowContent` (full tabs, listeners, gear menu—not manual tab wiring)
3. `HeadlessToolWindowHarness.teardownLightUi` in `@AfterEach` (close editors, clear tool window contents, release editors)

## Page objects

Light UI tests interact with the product through **page objects** (e.g. `SonarLintMainToolWindowPage` → `currentFile` → `issues`), not production panel classes. Subclass [`AbstractSonarLintUILightTests`](../src/test/java/org/sonarlint/intellij/AbstractSonarLintUILightTests.kt) to obtain a wired main tool window and automatic teardown.

Example assertion:

```kotlin
mainToolWindow.currentFile.refreshForFile(file)
assertThat(mainToolWindow.currentFile.issues).hasSize(1)
```

## Prefer extension points

| Surface | Entry point |
| --- | --- |
| Findings tool window | `SonarLintToolWindowFactory` |
| Walkthrough | `SonarLintWalkthroughToolWindowFactory` |
| Editor traffic light | `InspectionWidgetActionProvider` (`SonarLintEditorWidgetActionProvider`) |
| Notifications | `SonarLintProjectNotifications`, `NotificationGroup` EP, `Notifications.TOPIC` |
| Settings | `applicationConfigurable` / `projectConfigurable` EP (`sonarlint` ids in `plugin.xml`) |
