/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.ide.GeneratedSourceFileChangeTracker;
import com.intellij.ide.bookmark.BookmarksManager;
import com.intellij.ide.favoritesTreeView.FavoritesManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.notification.impl.NotificationsToolWindowFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.annotate.AnnotationsPreloader;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.serviceContainer.ComponentManagerImpl;
import com.intellij.testFramework.PlatformTestUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.apache.commons.io.file.PathUtils;
import org.jetbrains.annotations.NotNull;
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
import org.sonarlint.intellij.config.global.credentials.CredentialsService;
import org.sonarlint.intellij.config.module.SonarLintModuleSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.fixtures.AbstractLightTests;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;
import org.sonarlint.intellij.ui.SonarLintConsoleTestImpl;

import static com.intellij.notification.NotificationsManager.getNotificationsManager;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public abstract class AbstractSonarLintLightTests extends AbstractLightTests {

  private Disposable disposable;
  private BackendService realBackendService;
  protected static final Path storageRoot = Paths.get(PathManager.getSystemPath()).resolve("sonarlint").resolve("storage");
  private final List<Path> createdTempDirs = new ArrayList<>();
  private final List<Notification> deliveredProjectNotifications = new CopyOnWriteArrayList<>();

  public @NotNull Disposable getApplicationLevelDisposable() {
    return getService(AnchorApplicationService.class);
  }

  @Override
  protected void initializeProjectLifetimeServices() {
    super.initializeProjectLifetimeServices();
    // These platform services attach project-owned listeners when the first editor is used.
    AutoPopupController.getInstance(getProject());
    LookupManager.getInstance(getProject());
    TemplateManager.getInstance(getProject());
    BookmarksManager.getInstance(getProject());
    FavoritesManager.getInstance(getProject());
    GeneratedSourceFileChangeTracker.getInstance(getProject());
    getProject().getService(AnnotationsPreloader.class);
    // The Action Center keeps one notification model for the lifetime of the reused project.
    var notification = new Notification("System Messages", "", NotificationType.INFORMATION);
    NotificationsToolWindowFactory.Companion.addNotification(getProject(), notification);
    notification.expire();
  }

  @Override
  protected final String getTestDataPath() {
    return Paths.get("src/test/testData/" + this.getClass().getSimpleName()).toAbsolutePath().toString();
  }

  @BeforeAll
  static void clearStorageRoot() {
    if (Files.exists(storageRoot)) {
      try {
        PathUtils.deleteDirectory(storageRoot);
      } catch (IOException e) {
        // Ignore, this is just a cleanup
      }
    }
  }

  @BeforeEach
  final void beforeEach() {
    disposable = Disposer.newDisposable(getTestRootDisposable(), "SonarLint light test scratch");
    getProject().getMessageBus().connect(disposable).subscribe(Notifications.TOPIC, new Notifications() {
      @Override
      public void notify(@NotNull Notification notification) {
        deliveredProjectNotifications.add(notification);
      }
    });
    realBackendService = getService(BackendService.class);
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
    realBackendService.connectionsUpdated(Collections.emptyList());
    realBackendService.projectOpened(getProject());
    realBackendService.modulesAdded(getProject(), List.of(getModule()));
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
    createdTempDirs.forEach(dir -> {
      try {
        PathUtils.deleteDirectory(dir);
      } catch (IOException e) {
        e.printStackTrace();
      }
    });
    createdTempDirs.clear();
    // Connections are removed before the next test, their saved credentials would stay
    var credentialsService = getService(CredentialsService.class);
    var connections = List.copyOf(getGlobalSettings().getServerConnections());
    if (!connections.isEmpty()) {
      runFixtureIo(() -> {
        connections.forEach(credentialsService::eraseCredentials);
        return null;
      });
    }
    TestDialogManager.setTestDialog(TestDialog.DEFAULT);
    deliveredProjectNotifications.forEach(Notification::expire);
    if (!getProject().isDisposed()) {
      clearNotifications();
    }
    realBackendService.projectClosed(getProject());
    if (!getProject().isDisposed()) {
      getService(getProject(), RunningAnalysesTracker.class).cancelAll();
      AnalysisStatus.get(getProject()).stopRun();
    }
    Disposer.dispose(disposable);
  }

  protected void clearNotifications() {
    var mgr = getNotificationsManager();
    Stream.of(mgr.getNotificationsOfType(Notification.class, getProject()))
      .forEach(Notification::expire);
  }

  /**
   * Starts recording the notifications published on the application message bus until the end of the test, whether
   * they expire or not. Call it before the action that notifies.
   *
   * @return a live view of the notifications recorded so far, oldest first
   */
  protected List<Notification> recordNotifications() {
    var recorded = new CopyOnWriteArrayList<Notification>();
    ApplicationManager.getApplication().getMessageBus().connect(disposable).subscribe(Notifications.TOPIC, new Notifications() {
      @Override
      public void notify(@NotNull Notification notification) {
        recorded.add(notification);
      }
    });
    return Collections.unmodifiableList(recorded);
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

  protected VirtualFile createTestFile(String fileName, String text) {
    return createTestPsiFile(fileName, text).getVirtualFile();
  }

  protected VirtualFile createAndOpenTestVirtualFile(String fileName, String text) {
    var file = createTestFile(fileName, text);
    FileEditorManager.getInstance(getProject()).openFile(file, false);
    return file;
  }

  protected PsiFile createAndOpenTestPsiFile(String fileName, String text) {
    var file = createTestPsiFile(fileName, text);
    FileEditorManager.getInstance(getProject()).openFile(file.getVirtualFile(), false);
    return file;
  }

  protected PsiFile createTestPsiFile(String fileName, String text) {
    if (createdTempDirs.isEmpty()) {
      var tempDir = runFixtureIo(() -> Files.createTempDirectory("sonarlint-light-test-src"));
      createdTempDirs.add(tempDir);
      var virtualDir = runFixtureIo(() -> LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempDir));
      WriteAction.runAndWait(() -> ModuleRootModificationUtil.updateModel(getModule(), model ->
        model.addContentEntry(virtualDir).addSourceFolder(virtualDir, false)));
    }

    var file = createdTempDirs.get(0).resolve(fileName);
    var project = getProject();
    return runFixtureIo(() -> {
      Files.createDirectories(file.getParent());
      Files.writeString(file, text);
      var fileSystem = LocalFileSystem.getInstance();
      var virtualFile = fileSystem.refreshAndFindFileByNioFile(file);
      if (virtualFile == null) {
        var parent = Objects.requireNonNull(fileSystem.refreshAndFindFileByNioFile(file.getParent()));
        parent.refresh(false, false);
        virtualFile = parent.findChild(file.getFileName().toString());
      }
      var refreshedFile = Objects.requireNonNull(virtualFile, "Test file not found after refresh: " + file);
      return ReadAction.compute(() -> PsiManager.getInstance(project).findFile(refreshedFile));
    });
  }

  protected static <T> T runFixtureIo(Callable<T> task) {
    return PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread(task));
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
