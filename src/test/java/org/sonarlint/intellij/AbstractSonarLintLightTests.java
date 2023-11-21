/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.intellij;

import com.intellij.lang.Language;
import com.intellij.notification.Notification;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.serviceContainer.ComponentManagerImpl;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.file.PathUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarlint.intellij.analysis.AnalysisStatus;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.config.Settings;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.config.global.ServerConnectionCredentials;
import org.sonarlint.intellij.config.global.ServerConnectionService;
import org.sonarlint.intellij.config.global.ServerConnectionWithAuth;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.module.SonarLintModuleSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.core.EngineManager;
import org.sonarlint.intellij.core.ProjectBinding;
import org.sonarlint.intellij.core.TestEngineManager;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;
import org.sonarlint.intellij.ui.SonarLintConsoleTestImpl;

import static com.intellij.notification.NotificationsManager.getNotificationsManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;
import static org.sonarlint.intellij.fixtures.ServerConnectionFixturesKt.newSonarQubeConnection;

@ExtendWith(RunInEdtInterceptor.class)
public abstract class AbstractSonarLintLightTests extends BasePlatformTestCase {

  private Disposable disposable;
  protected static final Path storageRoot = Paths.get(PathManager.getSystemPath()).resolve("sonarlint").resolve("storage");

  @Override
  protected final String getTestDataPath() {
    return Paths.get("src/test/testData/" + this.getClass().getSimpleName()).toAbsolutePath().toString();
  }

  @BeforeAll
  static void clearStorageRoot() throws IOException {
    if (Files.exists(storageRoot)) {
      PathUtils.deleteDirectory(storageRoot);
    }
  }

  @BeforeEach
  final void beforeEachLightTest(TestInfo testInfo) throws Exception {
    // explicitly call TestCase.setName as IntelliJ relies on it for the setup
    setName(testInfo.getTestMethod().map(Method::getName).orElse("test"));
    super.setUp();
    disposable = Disposer.newDisposable();
    getGlobalSettings().setRules(Collections.emptyList());
    setServerConnections(Collections.emptyList());
    getGlobalSettings().setFocusOnNewCode(false);
    setGlobalLevelExclusions(Collections.emptyList());
    getProjectSettings().setConnectionName(null);
    getProjectSettings().setProjectKey(null);
    getProjectSettings().setBindingEnabled(false);
    getProjectSettings().setBindingSuggestionsEnabled(true);
    setProjectLevelExclusions(Collections.emptyList());
    getModuleSettings().setIdePathPrefix("");
    getModuleSettings().setSqPathPrefix("");
    getModuleSettings().clearBindingOverride();
    // connections might have been removed, let time for the storage to be cleaned up by the backend
    await().atMost(Duration.ofSeconds(3))
      .untilAsserted(() -> assertThat(storageRoot).satisfiesAnyOf(
        root -> assertThat(root).doesNotExist(),
        root -> assertThat(root).isEmptyDirectory()));
    getService(BackendService.class).projectUnbound(getProject());
  }

  @RegisterExtension
  AfterTestExecutionCallback afterTestExecutionCallback = context -> {
    Optional<Throwable> exception = context.getExecutionException();
    var console = getConsole();
    if (console instanceof SonarLintConsoleTestImpl testConsole && exception.isPresent()) {
      var testName = context.getDisplayName();
      System.out.println("Test '" + testName + "' failed. Logs:");
      testConsole.flushTo(System.out);
      System.out.println("End of logs for test '" + testName + "'");
    } else {
      console.clear();
    }
  };

  @AfterEach
  final void afterEachLightTest() throws Exception {
    try {
      if (!getProject().isDisposed()) {
        AnalysisStatus.get(getProject()).stopRun();
      }
      Disposer.dispose(disposable);

    } finally {
      super.tearDown();
    }
  }

  @AfterAll
  static void stopEngines() {
    try {
      getEngineManager().stopAllEngines(false);
    } catch (Exception e) {
      System.out.println("Error stoping all engines");
      e.printStackTrace();
    }
  }

  protected void clearNotifications() {
    var mgr = getNotificationsManager();
    Stream.of(mgr.getNotificationsOfType(Notification.class, getProject()))
      .forEach(mgr::expire);
  }

  protected List<Notification> getProjectNotifications() {
    return List.of(getNotificationsManager().getNotificationsOfType(Notification.class, getProject()));
  }

  protected <T> void replaceProjectService(Class<T> clazz, T newInstance) {
    ((ComponentManagerImpl) getProject()).replaceServiceInstance(clazz, newInstance, disposable);
  }

  protected <T> void replaceModuleService(Class<T> clazz, T newInstance) {
    ((ComponentManagerImpl) getModule()).replaceServiceInstance(clazz, newInstance, disposable);
  }

  protected SonarLintGlobalSettings getGlobalSettings() {
    return Settings.getGlobalSettings();
  }

  protected SonarLintProjectSettings getProjectSettings() {
    return getSettingsFor(getProject());
  }

  protected SonarLintModuleSettings getModuleSettings() {
    return getSettingsFor(getModule());
  }

  protected static TestEngineManager getEngineManager() {
    return (TestEngineManager) getService(EngineManager.class);
  }

  protected SonarLintConsole getConsole() {
    return getService(getProject(), SonarLintConsole.class);
  }

  protected VirtualFile createTestFile(String fileName, Language language, String text) {
    return createTestPsiFile(fileName, language, text).getVirtualFile();
  }

  protected VirtualFile createAndOpenTestVirtualFile(String fileName, Language language, String text) {
    var file = createTestFile(fileName, language, text);
    FileEditorManager.getInstance(getProject()).openFile(file, false);
    return file;
  }

  protected PsiFile createAndOpenTestPsiFile(String fileName, Language language, String text) {
    var file = createTestPsiFile(fileName, language, text);
    FileEditorManager.getInstance(getProject()).openFile(file.getVirtualFile(), false);
    return file;
  }

  protected PsiFile createTestPsiFile(String fileName, Language language, String text) {
    return PsiFileFactory.getInstance(getProject()).createFileFromText(fileName, language, text, true, true);
  }

  protected void connectProjectTo(String hostUrl, String connectionName, String projectKey) {
    var connection = newSonarQubeConnection(connectionName, hostUrl);
    ServerConnectionService.getInstance().addServerConnection(new ServerConnectionWithAuth(connection, new ServerConnectionCredentials(null, null, "token")));
    getProjectSettings().bindTo(connection, projectKey);
    getService(BackendService.class).projectBound(getProject(), new ProjectBinding(connectionName, projectKey, Collections.emptyMap()));
  }

  protected void setServerConnections(List<ServerConnection> connections) {
    ServerConnectionService.getInstance().updateServerConnections(getGlobalSettings(), ServerConnectionService.getInstance().getConnections().stream().map(ServerConnection::getName).collect(Collectors.toSet()),
      Collections.emptyList(), connections.stream().map(connection -> new ServerConnectionWithAuth(connection, new ServerConnectionCredentials(null, null, "token"))).collect(Collectors.toList()));
  }

  protected void connectProjectTo(ServerConnection connection, String projectKey) {
    ServerConnectionService.getInstance().addServerConnection(new ServerConnectionWithAuth(connection, new ServerConnectionCredentials(null, null, "token")));
    getProjectSettings().bindTo(connection, projectKey);
  }

  protected void setProjectLevelExclusions(List<String> exclusions) {
    var projectSettings = getProjectSettings();
    projectSettings.setFileExclusions(exclusions);
    ProjectConfigurationListener projectListener = getProject().getMessageBus().syncPublisher(ProjectConfigurationListener.TOPIC);
    projectListener.changed(projectSettings);
  }

  protected void setGlobalLevelExclusions(List<String> exclusions) {
    getGlobalSettings().setFileExclusions(exclusions);
  }

  protected String getProjectBackendId() {
    return getProject().getProjectFilePath();
  }
}
