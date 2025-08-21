/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
import com.intellij.notification.Notification;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.serviceContainer.ComponentManagerImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.io.file.PathUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonarlint.intellij.analysis.AnalysisStatus;
import org.sonarlint.intellij.analysis.RunningAnalysesTracker;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.config.Settings;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.module.SonarLintModuleSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.core.ProjectBinding;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;
import org.sonarlint.intellij.test.AbstractLightTests;
import org.sonarlint.intellij.ui.SonarLintConsoleTestImpl;

import static com.intellij.notification.NotificationsManager.getNotificationsManager;
import static org.sonarlint.intellij.SonarLintTestUtils.clearServerConnectionCredentials;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public abstract class AbstractSonarLintLightTests extends AbstractLightTests {

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
  final void beforeEach() {
    disposable = Disposer.newDisposable();
    clearServerConnectionCredentials();
    getGlobalSettings().setRules(Collections.emptyList());
    getGlobalSettings().setServerConnections(Collections.emptyList());
    getGlobalSettings().setFocusOnNewCode(false);
    setGlobalLevelExclusions(Collections.emptyList());
    getProjectSettings().setConnectionName(null);
    getProjectSettings().setProjectKey(null);
    getProjectSettings().setBindingEnabled(false);
    getProjectSettings().setBindingSuggestionsEnabled(true);
    getProjectSettings().setVerboseEnabled(true);
    setProjectLevelExclusions(Collections.emptyList());
    getModuleSettings().clearBindingOverride();
    getService(BackendService.class).connectionsUpdated(Collections.emptyList());
    getService(BackendService.class).projectOpened(getProject());
    getService(BackendService.class).modulesAdded(getProject(), List.of(getModule()));
    PropertiesComponent.getInstance().unsetValue("SonarLint.lastPromotionNotificationDate");
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
  final void afterEach() {
    getService(BackendService.class).moduleRemoved(getModule());
    getService(BackendService.class).projectClosed(getProject());
    if (!getProject().isDisposed()) {
      getService(getProject(), RunningAnalysesTracker.class).cancelAll();
      AnalysisStatus.get(getProject()).stopRun();
    }
    Disposer.dispose(disposable);
  }

  protected void clearNotifications() {
    var mgr = getNotificationsManager();
    Stream.of(mgr.getNotificationsOfType(Notification.class, getProject()))
      .forEach(mgr::expire);
  }

  protected List<Notification> getProjectNotifications() {
    return List.of(getNotificationsManager().getNotificationsOfType(Notification.class, getProject()));
  }

  protected <T> void replaceApplicationService(Class<T> clazz, T newInstance) {
    ((ComponentManagerImpl) ApplicationManager.getApplication()).replaceServiceInstance(clazz, newInstance, disposable);
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
    getService(BackendService.class).connectionsUpdated(getGlobalSettings().getServerConnections());
    getService(BackendService.class).projectBound(getProject(), new ProjectBinding(connectionName, projectKey, Collections.emptyMap()));
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
