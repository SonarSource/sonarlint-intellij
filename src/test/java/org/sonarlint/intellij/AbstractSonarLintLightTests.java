/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
import com.intellij.notification.NotificationsManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.serviceContainer.ComponentManagerImpl;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.sonarlint.intellij.analysis.AnalysisStatus;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.config.Settings;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.module.SonarLintModuleSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;
import org.sonarlint.intellij.ui.SonarLintConsoleTestImpl;
import org.sonarlint.intellij.util.SonarLintUtils;

import static com.intellij.notification.NotificationsManager.getNotificationsManager;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public abstract class AbstractSonarLintLightTests extends LightPlatformCodeInsightFixture4TestCase {

  private Disposable disposable;

  @Override
  protected final String getTestDataPath() {
    return Paths.get("src/test/testData/" + this.getClass().getSimpleName()).toAbsolutePath().toString();
  }

  @Before
  public final void init() {
    disposable = Disposer.newDisposable();
  }

  @After
  public final void restore() {
    getGlobalSettings().setRules(Collections.emptyList());
    setGlobalLevelExclusions(Collections.emptyList());
    getProjectSettings().setProjectKey(null);
    getProjectSettings().setBindingEnabled(false);
    setProjectLevelExclusions(Collections.emptyList());
    getModuleSettings().setIdePathPrefix("");
    getModuleSettings().setSqPathPrefix("");
    if (!getProject().isDisposed()) {
      AnalysisStatus.get(getProject()).stopRun();
    }
    Disposer.dispose(disposable);
  }

  protected void clearNotifications() {
    NotificationsManager mgr = getNotificationsManager();
    Arrays.stream(mgr.getNotificationsOfType(Notification.class, getProject()))
      .forEach(mgr::expire);
  }

  protected List<Notification> getProjectNotifications() {
    return Arrays.asList(getNotificationsManager().getNotificationsOfType(Notification.class, getProject()));
  }

  protected <T> void replaceProjectService(Class<T> clazz, T newInstance) {
    ((ComponentManagerImpl) getProject()).replaceServiceInstance(clazz, newInstance, disposable);
  }

  public SonarLintGlobalSettings getGlobalSettings() {
    return Settings.getGlobalSettings();
  }

  public SonarLintProjectSettings getProjectSettings() {
    return getSettingsFor(getProject());
  }

  public SonarLintModuleSettings getModuleSettings() {
    return getSettingsFor(getModule());
  }

  protected SonarLintConsoleTestImpl getConsole() {
    return (SonarLintConsoleTestImpl) SonarLintUtils.getService(getProject(), SonarLintConsole.class);
  }

  public VirtualFile createTestFile(String fileName, Language language, String text) {
    return createTestPsiFile(fileName, language, text).getVirtualFile();
  }

  public VirtualFile createAndOpenTestVirtualFile(String fileName, Language language, String text) {
    VirtualFile file = createTestFile(fileName, language, text);
    FileEditorManager.getInstance(getProject()).openFile(file, false);
    return file;
  }

  public PsiFile createAndOpenTestPsiFile(String fileName, Language language, String text) {
    PsiFile file = createTestPsiFile(fileName, language, text);
    FileEditorManager.getInstance(getProject()).openFile(file.getVirtualFile(), false);
    return file;
  }

  public PsiFile createTestPsiFile(String fileName, Language language, String text) {
    return PsiFileFactory.getInstance(getProject()).createFileFromText(fileName, language, text, true, true);
  }

  protected void connectProjectTo(String hostUrl, String connectionName, String projectKey) {
    ServerConnection connection = ServerConnection.newBuilder().setHostUrl(hostUrl).setName(connectionName).build();
    getGlobalSettings().addServerConnection(connection);
    getProjectSettings().bindTo(connection, projectKey);
  }

  protected void connectProjectTo(ServerConnection connection, String projectKey) {
    getGlobalSettings().addServerConnection(connection);
    getProjectSettings().bindTo(connection, projectKey);
  }

  protected void setProjectLevelExclusions(List<String> exclusions) {
    SonarLintProjectSettings projectSettings = getProjectSettings();
    projectSettings.setFileExclusions(exclusions);
    ProjectConfigurationListener projectListener = getProject().getMessageBus().syncPublisher(ProjectConfigurationListener.TOPIC);
    projectListener.changed(projectSettings);
  }

  protected void setGlobalLevelExclusions(List<String> exclusions) {
    SonarLintGlobalSettings globalSettings = getGlobalSettings();
    globalSettings.setFileExclusions(exclusions);
    GlobalConfigurationListener globalConfigurationListener = ApplicationManager.getApplication()
      .getMessageBus().syncPublisher(GlobalConfigurationListener.TOPIC);
    globalConfigurationListener.applied(globalSettings);
  }
}
