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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.serviceContainer.ComponentManagerImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.sonarlint.intellij.config.module.SonarLintModuleSettings;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.fs.VirtualFileEvent;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent;
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
  private final List<Path> moduleScopePathsToDelete = new ArrayList<>();
  private final List<VirtualFile> moduleScopeContentRootsToRemove = new ArrayList<>();

  public @NotNull Disposable getApplicationLevelDisposable() {
    return getService(AnchorApplicationService.class);
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
    disposable = Disposer.newDisposable();
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
    cleanupModuleScopeArtifacts();
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

  /**
   * Writable module content root on the local filesystem (not {@code temp://} VFS).
   * Required for IaC Helm analysis in SLOOP.
   */
  protected Path ensureDiskContentRoot() {
    try {
      if (createdTempDirs.isEmpty()) {
        var tempDir = Files.createTempDirectory("sonarlint-light-test-src");
        createdTempDirs.add(tempDir);
        var virtualDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempDir);
        WriteAction.runAndWait(() -> ModuleRootModificationUtil.updateModel(getModule(), model ->
          model.addContentEntry(virtualDir).addSourceFolder(virtualDir, false)));
      }
      return createdTempDirs.get(0);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * On-disk root of the light-test module (same tree as the UI IT project root when opening {@code its/projects/...}).
   * Helm/IaC analysis resolves {@code Chart.yaml} relative to this scope, not a separate temp content root.
   */
  protected Path moduleScopeRootOnDisk() {
    var basePath = getProject().getBasePath();
    if (basePath != null) {
      return Paths.get(basePath);
    }
    var projectFile = getProject().getProjectFilePath();
    if (projectFile != null) {
      return Paths.get(projectFile).getParent();
    }
    throw new IllegalStateException("Light test project has no on-disk root");
  }

  /** Copies a directory tree into {@link #moduleScopeRootOnDisk()} and refreshes the VFS. */
  protected void copyDirectoryIntoModuleScopeRoot(Path sourceDirectory) {
    try {
      var targetRoot = moduleScopeRootOnDisk();
      var copiedTreeRoot = targetRoot.resolve(sourceDirectory.getFileName());
      try (var paths = Files.walk(sourceDirectory)) {
        paths.forEach(source -> {
          try {
            var relative = sourceDirectory.relativize(source);
            var target = copiedTreeRoot.resolve(relative);
            if (Files.isDirectory(source)) {
              Files.createDirectories(target);
            } else {
              Files.createDirectories(target.getParent());
              Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
      }
      moduleScopePathsToDelete.add(copiedTreeRoot);
      var virtualCopiedDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(copiedTreeRoot);
      var copiedDirNeedsContentRoot = virtualCopiedDir != null && Arrays.stream(ModuleRootManager.getInstance(getModule()).getContentRoots())
        .noneMatch(root -> isSameOrAncestor(root, virtualCopiedDir));
      if (copiedDirNeedsContentRoot) {
        moduleScopeContentRootsToRemove.add(virtualCopiedDir);
        WriteAction.runAndWait(() -> ModuleRootModificationUtil.updateModel(getModule(), model ->
          model.addContentEntry(virtualCopiedDir).addSourceFolder(virtualCopiedDir, false)));
      }
      VfsUtil.markDirtyAndRefresh(false, true, true, virtualCopiedDir);
      notifyBackendOfCopiedTree(sourceDirectory, copiedTreeRoot);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected void writeModuleScopeFile(String relativePath, String content) {
    try {
      var path = moduleScopeRootOnDisk().resolve(relativePath);
      moduleScopePathsToDelete.add(path);
      Files.createDirectories(path.getParent());
      Files.writeString(path, content);
      var virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
      if (virtualFile != null) {
        getService(BackendService.class).updateFileSystem(
          Map.of(getModule(), List.of(new VirtualFileEvent(ModuleFileEvent.Type.MODIFIED, virtualFile))),
          true);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void notifyBackendOfCopiedTree(Path sourceDirectory, Path copiedTreeRoot) {
    try {
      var events = new ArrayList<VirtualFileEvent>();
      try (var paths = Files.walk(sourceDirectory)) {
        paths.filter(Files::isRegularFile).forEach(source -> {
          var relative = sourceDirectory.relativize(source);
          var target = copiedTreeRoot.resolve(relative);
          var virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(target);
          if (virtualFile != null) {
            events.add(new VirtualFileEvent(ModuleFileEvent.Type.CREATED, virtualFile));
          }
        });
      }
      if (!events.isEmpty()) {
        getService(BackendService.class).updateFileSystem(Map.of(getModule(), events), true);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** Copies a directory tree into {@link #ensureDiskContentRoot()} and refreshes the VFS. */
  protected void copyDirectoryIntoDiskContentRoot(Path sourceDirectory) {
    try {
      var targetRoot = ensureDiskContentRoot();
      try (var paths = Files.walk(sourceDirectory)) {
        paths.forEach(source -> {
          try {
            var relative = sourceDirectory.relativize(source);
            var target = targetRoot.resolve(relative);
            if (Files.isDirectory(source)) {
              Files.createDirectories(target);
            } else {
              Files.createDirectories(target.getParent());
              Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
      }
      var virtualDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(targetRoot);
      VfsUtil.markDirtyAndRefresh(false, true, true, virtualDir);
      notifyBackendOfFilesUnderDiskContentRoot();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected void notifyBackendOfFilesUnderDiskContentRoot() {
    try {
      var root = ensureDiskContentRoot();
      var events = new ArrayList<VirtualFileEvent>();
      try (var paths = Files.walk(root)) {
        paths.filter(Files::isRegularFile).forEach(path -> {
          var virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
          if (virtualFile != null) {
            events.add(new VirtualFileEvent(ModuleFileEvent.Type.CREATED, virtualFile));
          }
        });
      }
      getService(BackendService.class).updateFileSystem(Map.of(getModule(), events), true);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected PsiFile createTestPsiFile(String fileName, String text) {
    try {
      var root = ensureDiskContentRoot();
      var file = root.resolve(fileName);
      Files.createDirectories(file.getParent());
      Files.writeString(file, text);
      var virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
      return PsiManager.getInstance(getProject()).findFile(virtualFile);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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

  private static boolean isSameOrAncestor(VirtualFile ancestor, VirtualFile file) {
    var current = file;
    while (current != null) {
      if (current.equals(ancestor)) {
        return true;
      }
      current = current.getParent();
    }
    return false;
  }

  private void cleanupModuleScopeArtifacts() {
    for (var path : moduleScopePathsToDelete) {
      try {
        if (Files.isDirectory(path)) {
          PathUtils.deleteDirectory(path);
        } else if (Files.exists(path)) {
          Files.delete(path);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    moduleScopePathsToDelete.clear();
    for (var contentRoot : moduleScopeContentRootsToRemove) {
      WriteAction.runAndWait(() -> ModuleRootModificationUtil.updateModel(getModule(), model -> {
        for (var entry : model.getContentEntries()) {
          if (entry.getFile() != null && entry.getFile().equals(contentRoot)) {
            model.removeContentEntry(entry);
          }
        }
      }));
    }
    moduleScopeContentRootsToRemove.clear();
  }
}
