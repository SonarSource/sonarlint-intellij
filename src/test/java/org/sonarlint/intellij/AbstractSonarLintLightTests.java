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
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.serviceContainer.ComponentManagerImpl;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarlint.intellij.analysis.AnalysisStatus;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.config.Settings;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.module.SonarLintModuleSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.core.EngineManager;
import org.sonarlint.intellij.core.TestEngineManager;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;
import org.sonarlint.intellij.ui.SonarLintConsoleTestImpl;

import static com.intellij.notification.NotificationsManager.getNotificationsManager;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

@ExtendWith(RunInEdtInterceptor.class)
public abstract class AbstractSonarLintLightTests extends BasePlatformTestCase {

  private Disposable disposable;

  @Override
  protected final String getTestDataPath() {
    return Paths.get("src/test/testData/" + this.getClass().getSimpleName()).toAbsolutePath().toString();
  }

  @BeforeEach
  final void beforeEachLightTest(TestInfo testInfo) throws Exception {
    // explicitly call TestCase.setName as IntelliJ relies on it for the setup
    setName(testInfo.getTestMethod().map(Method::getName).orElse("test"));
    super.setUp();
    disposable = Disposer.newDisposable();
    getGlobalSettings().setRules(Collections.emptyList());
    getGlobalSettings().setServerConnections(Collections.emptyList());
    setGlobalLevelExclusions(Collections.emptyList());
    getProjectSettings().setConnectionName(null);
    getProjectSettings().setProjectKey(null);
    getProjectSettings().setBindingEnabled(false);
    getProjectSettings().setBindingSuggestionsEnabled(true);
    setProjectLevelExclusions(Collections.emptyList());
    getModuleSettings().setIdePathPrefix("");
    getModuleSettings().setSqPathPrefix("");
    getModuleSettings().clearBindingOverride();
  }

  @RegisterExtension
  AfterTestExecutionCallback afterTestExecutionCallback = context -> {
    Optional<Throwable> exception = context.getExecutionException();
    var console = getConsole();
    if (console instanceof SonarLintConsoleTestImpl && exception.isPresent()) {
      var testConsole = (SonarLintConsoleTestImpl) console;
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
      getEngineManager().stopAllEngines(false);
      if (!getProject().isDisposed()) {
        AnalysisStatus.get(getProject()).stopRun();
      }
      Disposer.dispose(disposable);

    } finally {
      super.tearDown();
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

  protected TestEngineManager getEngineManager() {
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
    var connection = ServerConnection.newBuilder().setHostUrl(hostUrl).setName(connectionName).build();
    getGlobalSettings().addServerConnection(connection);
    getProjectSettings().bindTo(connection, projectKey);
  }

  protected void connectModuleTo(String projectKey) {
    getModuleSettings().setProjectKey(projectKey);
  }

  protected void connectProjectTo(ServerConnection connection, String projectKey) {
    getGlobalSettings().addServerConnection(connection);
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
