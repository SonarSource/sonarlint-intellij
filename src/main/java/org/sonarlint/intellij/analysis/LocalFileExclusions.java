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
package org.sonarlint.intellij.analysis;

import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.analysis.ExcludeResult;
import org.sonarlint.intellij.common.analysis.FileExclusionContributor;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.project.ExclusionItem;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.core.SonarLintFacade;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.common.FileExclusions;

import static org.sonarlint.intellij.config.Settings.getGlobalSettings;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public class LocalFileExclusions {
  private final Project myProject;

  private FileExclusions projectExclusions;
  private FileExclusions globalExclusions;

  public LocalFileExclusions(Project project) {
    this.myProject = project;
    subscribeToSettingsChanges(project);
    loadGlobalExclusions(getGlobalSettings());
    loadProjectExclusions(getSettingsFor(project));
  }

  private static Set<String> getExclusionsOfType(Collection<ExclusionItem> exclusions, ExclusionItem.Type type) {
    return exclusions.stream()
      .filter(e -> e.type() == type)
      .map(ExclusionItem::item)
      .collect(Collectors.toSet());
  }

  private void loadProjectExclusions(SonarLintProjectSettings settings) {
    List<ExclusionItem> projectExclusionsItems = settings.getFileExclusions().stream()
      .map(ExclusionItem::parse)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    Set<String> projectFileExclusions = getExclusionsOfType(projectExclusionsItems, ExclusionItem.Type.FILE);
    Set<String> projectDirExclusions = getExclusionsOfType(projectExclusionsItems, ExclusionItem.Type.DIRECTORY);
    Set<String> projectGlobExclusions = getExclusionsOfType(projectExclusionsItems, ExclusionItem.Type.GLOB);

    this.projectExclusions = new FileExclusions(projectFileExclusions, projectDirExclusions, projectGlobExclusions);
  }

  private void loadGlobalExclusions(SonarLintGlobalSettings settings) {
    this.globalExclusions = new FileExclusions(new LinkedHashSet<>(settings.getFileExclusions()));
  }

  private void subscribeToSettingsChanges(Project project) {
    MessageBusConnection busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(GlobalConfigurationListener.TOPIC, new GlobalConfigurationListener.Adapter() {
      @Override
      public void applied(SonarLintGlobalSettings newSettings) {
        loadGlobalExclusions(newSettings);
      }
    });
    busConnection.subscribe(ProjectConfigurationListener.TOPIC, this::loadProjectExclusions);
  }

  /**
   * Checks if a file is excluded from analysis based on locally configured exclusions.
   */
  private ExcludeResult checkExclusionsFromSonarLintSettings(VirtualFile file, Module module) {
    String relativePath = SonarLintAppUtils.getRelativePathForAnalysis(module, file);
    if (relativePath == null) {
      return ExcludeResult.excluded("Could not create a relative path");
    }
    if (globalExclusions.test(relativePath)) {
      return ExcludeResult.excluded("file matches exclusions defined in the SonarLint Global Settings");
    }
    if (projectExclusions.test(relativePath)) {
      return ExcludeResult.excluded("file matches exclusions defined in the SonarLint Project Settings");
    }

    return ExcludeResult.notExcluded();
  }

  private ExcludeResult checkFileInSourceFolders(VirtualFile file, Module module) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    SourceFolder sourceFolder = SonarLintUtils.getSourceFolder(fileIndex.getSourceRootForFile(file), module);
    if (sourceFolder != null) {
      if (SonarLintUtils.isGeneratedSource(sourceFolder)) {
        return ExcludeResult.excluded("file is classified as generated in project structure");
      }
      if (SonarLintUtils.isJavaResource(sourceFolder)) {
        return ExcludeResult.excluded("file is classified as Java resource in project structure");
      }
    }

    // the fact that the file doesn't explicitly belong to sources doesn't mean it's not sources.
    // In WebStorm, for example, everything is considered to be sources unless it is explicitly marked otherwise.
    return ExcludeResult.notExcluded();
  }

  public Map<Module, Collection<VirtualFile>> retainNonExcludedFilesByModules(Collection<VirtualFile> files, boolean forcedAnalysis,
    BiConsumer<VirtualFile, ExcludeResult> excludedFileHandler)
    throws InvalidBindingException {
    Map<Module, Collection<VirtualFile>> filesByModule = new LinkedHashMap<>();

    for (VirtualFile file : files) {
      checkExclusionsFileByFile(forcedAnalysis, excludedFileHandler, filesByModule, file);
    }
    if (!forcedAnalysis && !filesByModule.isEmpty()) {
      // Apply server file exclusions. This is an expensive operation, so we call the core only once per module.
      filterWithServerExclusions(excludedFileHandler, filesByModule);
    }

    return filesByModule;
  }

  private void checkExclusionsFileByFile(boolean forcedAnalysis, BiConsumer<VirtualFile, ExcludeResult> excludedFileHandler,
    Map<Module, Collection<VirtualFile>> filesByModule, VirtualFile file) {
    Module m = SonarLintAppUtils.findModuleForFile(file, myProject);
    // Handle this case first, so that later we are guaranteed module is not null
    if (m == null) {
      excludedFileHandler.accept(file, ExcludeResult.excluded("file is not part of any module in IntelliJ's project structure"));
      return;
    }

    List<Supplier<ExcludeResult>> exclusionCheckers = new ArrayList<>(Arrays.asList(
      () -> excludeIfDisposed(m),
      LocalFileExclusions::excludeIfPowerSaveModeOn,
      () -> checkProjectStructureExclusion(file),
      () -> excludeUnsupportedFileOrFileType(file),
      () -> checkExclusionFromEP(file, m)));

    if (!forcedAnalysis) {
      exclusionCheckers.addAll(Arrays.asList(
        () -> checkFileInSourceFolders(file, m),
        () -> checkExclusionsFromSonarLintSettings(file, m)
      ));
    }

    for (Supplier<ExcludeResult> exclusionChecker : exclusionCheckers) {
      ExcludeResult result = exclusionChecker.get();
      if (result.isExcluded()) {
        excludedFileHandler.accept(file, result);
        return;
      }
    }

    filesByModule.computeIfAbsent(m, mod -> new LinkedHashSet<>()).add(file);
  }

  @NotNull
  private ExcludeResult checkProjectStructureExclusion(VirtualFile file) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    if (fileIndex.isExcluded(file)) {
      return ExcludeResult.excluded("file is excluded or ignored in project structure");
    }
    return ExcludeResult.notExcluded();
  }

  @NotNull
  private static ExcludeResult excludeUnsupportedFileOrFileType(VirtualFile file) {
    FileType fileType = file.getFileType();
    if (!file.isInLocalFileSystem() || fileType.isBinary() || !file.isValid() || ".idea".equals(file.getParent().getName())) {
      return ExcludeResult.excluded("file's type or location are not supported");
    }
    return ExcludeResult.notExcluded();
  }

  @NotNull
  private static ExcludeResult excludeIfPowerSaveModeOn() {
    if (PowerSaveMode.isEnabled()) {
      return ExcludeResult.excluded("power save mode is enabled");
    }
    return ExcludeResult.notExcluded();
  }

  @NotNull
  private static ExcludeResult excludeIfDisposed(Module m) {
    if (m.isDisposed()) {
      return ExcludeResult.excluded("module is disposed");
    } else if (m.getProject().isDisposed()) {
      return ExcludeResult.excluded("project is disposed");
    } else {
      return ExcludeResult.notExcluded();
    }
  }

  @NotNull
  private static ExcludeResult checkExclusionFromEP(VirtualFile file, Module m) {
    ExcludeResult excludeResultFromEp = ExcludeResult.notExcluded();
    for (FileExclusionContributor fileExclusion : FileExclusionContributor.EP_NAME.getExtensionList()) {
      excludeResultFromEp = fileExclusion.shouldExclude(m, file);
      if (excludeResultFromEp.isExcluded()) {
        break;
      }
    }
    return excludeResultFromEp;
  }

  private void filterWithServerExclusions(BiConsumer<VirtualFile, ExcludeResult> excludedFileHandler, Map<Module, Collection<VirtualFile>> filesByModule)
    throws InvalidBindingException {
    ProjectBindingManager projectBindingManager = SonarLintUtils.getService(myProject, ProjectBindingManager.class);
    SonarLintFacade sonarLintFacade = projectBindingManager.getFacade();
    // Note: iterating over a copy of keys, because removal of last value removes the key,
    // resulting in ConcurrentModificationException
    List<Module> modules = new ArrayList<>(filesByModule.keySet());
    for (Module module : modules) {
      Collection<VirtualFile> virtualFiles = filesByModule.get(module);
      Predicate<VirtualFile> testPredicate = f -> TestSourcesFilter.isTestSources(f, module.getProject());
      Collection<VirtualFile> excluded = sonarLintFacade.getExcluded(module, virtualFiles, testPredicate);
      for (VirtualFile f : excluded) {
        excludedFileHandler.accept(f, ExcludeResult.excluded("exclusions configured in the bound project"));
      }
      virtualFiles.removeAll(excluded);
      if (virtualFiles.isEmpty()) {
        filesByModule.remove(module);
      }
    }
  }

}
