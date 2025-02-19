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
package org.sonarlint.intellij.analysis;

import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.common.analysis.ExcludeResult;
import org.sonarlint.intellij.common.analysis.FileExclusionContributor;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.project.ExclusionItem;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;
import org.sonarsource.sonarlint.core.client.utils.ClientFileExclusions;

import static org.sonarlint.intellij.common.ui.ReadActionUtils.computeReadActionSafely;
import static org.sonarlint.intellij.config.Settings.getGlobalSettings;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;
import static org.sonarlint.intellij.util.SonarLintAppUtils.findModuleForFile;
import static org.sonarlint.intellij.util.SonarLintAppUtils.getRelativePathForAnalysis;

@Service(Service.Level.PROJECT)
public final class LocalFileExclusions {
  private final Project myProject;

  private ClientFileExclusions projectExclusions;
  private ClientFileExclusions globalExclusions;

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
    var projectExclusionsItems = settings.getFileExclusions().stream()
      .map(ExclusionItem::parse)
      .filter(Objects::nonNull)
      .toList();

    var projectFileExclusions = getExclusionsOfType(projectExclusionsItems, ExclusionItem.Type.FILE);
    var projectDirExclusions = getExclusionsOfType(projectExclusionsItems, ExclusionItem.Type.DIRECTORY);
    var projectGlobExclusions = getExclusionsOfType(projectExclusionsItems, ExclusionItem.Type.GLOB);

    this.projectExclusions = new ClientFileExclusions(projectFileExclusions, projectDirExclusions, projectGlobExclusions);
  }

  private void loadGlobalExclusions(SonarLintGlobalSettings settings) {
    this.globalExclusions = new ClientFileExclusions(Collections.emptySet(), Collections.emptySet(), new LinkedHashSet<>(settings.getFileExclusions()));
  }

  private void subscribeToSettingsChanges(Project project) {
    var busConnection = project.getMessageBus().connect();
    busConnection.subscribe(GlobalConfigurationListener.TOPIC, new GlobalConfigurationListener.Adapter() {
      @Override
      public void applied(SonarLintGlobalSettings previousSettings, SonarLintGlobalSettings newSettings) {
        loadGlobalExclusions(newSettings);
      }
    });
    busConnection.subscribe(ProjectConfigurationListener.TOPIC, (ProjectConfigurationListener) this::loadProjectExclusions);
  }

  /**
   * Checks if a file is excluded from analysis based on locally configured exclusions.
   */
  private ExcludeResult checkExclusionsFromSonarLintSettings(VirtualFile file, Module module) {
    var relativePath = getRelativePathForAnalysis(module, file);
    if (relativePath == null) {
      return ExcludeResult.excluded("Could not create a relative path");
    }
    if (globalExclusions.test(relativePath)) {
      return ExcludeResult.excluded("file matches exclusions defined in the SonarQube for IDE Global Settings");
    }
    if (projectExclusions.test(relativePath)) {
      return ExcludeResult.excluded("file matches exclusions defined in the SonarQube for IDE Project Settings");
    }

    return ExcludeResult.notExcluded();
  }

  private ExcludeResult checkVcsIgnored(VirtualFile file) {
    var fileStatusManager = FileStatusManager.getInstance(myProject);
    if (fileStatusManager.getStatus(file) == FileStatus.IGNORED) {
      return ExcludeResult.excluded("file is ignored in VCS");
    }
    return ExcludeResult.notExcluded();
  }

  private ExcludeResult checkFileInSourceFolders(VirtualFile file, Module module) {
    var fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    var sourceFolder = SonarLintUtils.getSourceFolder(fileIndex.getSourceRootForFile(file), module);
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
    BiConsumer<VirtualFile, ExcludeResult> excludedFileHandler) {
    var filesByModule = new LinkedHashMap<Module, Collection<VirtualFile>>();

    for (var file : files) {
      checkExclusionsFileByFile(forcedAnalysis, excludedFileHandler, filesByModule, file);
    }

    return filesByModule;
  }

  private void checkExclusionsFileByFile(boolean forcedAnalysis, BiConsumer<VirtualFile, ExcludeResult> excludedFileHandler,
    Map<Module, Collection<VirtualFile>> filesByModule, @Nullable VirtualFile file) {
    if (file == null) {
      excludedFileHandler.accept(null, ExcludeResult.excluded("file is not valid anymore"));
      return;
    }
    var module = findModuleForFile(file, myProject);
    // Handle this case first, so that later we are guaranteed module is not null
    if (module == null) {
      excludedFileHandler.accept(file, ExcludeResult.excluded("file is not part of any module in IntelliJ's project structure"));
      return;
    }

    var exclusionCheckers = Stream.concat(
      defaultExclusionCheckers(file, module),
      forcedAnalysis ? Stream.empty() : onTheFlyExclusionCheckers(file, module)).toList();

    for (var exclusionChecker : exclusionCheckers) {
      var result = computeReadActionSafely(module.getProject(), exclusionChecker::get);
      if (result != null && result.isExcluded()) {
        excludedFileHandler.accept(file, result);
        return;
      }
    }

    filesByModule.computeIfAbsent(module, mod -> new LinkedHashSet<>()).add(file);
  }

  @NotNull
  private Stream<Supplier<ExcludeResult>> onTheFlyExclusionCheckers(VirtualFile file, Module module) {
    return Stream.of(
      () -> checkVcsIgnored(file),
      () -> checkFileInSourceFolders(file, module),
      () -> checkExclusionsFromSonarLintSettings(file, module),
      LocalFileExclusions::excludeIfPowerSaveModeOn);
  }

  @NotNull
  private Stream<Supplier<ExcludeResult>> defaultExclusionCheckers(VirtualFile file, Module module) {
    return Stream.of(
      () -> excludeIfDisposed(module),
      () -> checkProjectStructureExclusion(file),
      () -> excludeUnsupportedFileOrFileType(file),
      () -> checkExclusionFromEP(file, module));
  }

  @NotNull
  private ExcludeResult checkProjectStructureExclusion(VirtualFile file) {
    var fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    if (fileIndex.isExcluded(file)) {
      return ExcludeResult.excluded("file is excluded or ignored in project structure");
    }
    return ExcludeResult.notExcluded();
  }

  @NotNull
  private static ExcludeResult excludeUnsupportedFileOrFileType(VirtualFile file) {
    var fileType = file.getFileType();
    if (!file.isInLocalFileSystem() || fileType.isBinary() || !file.isValid() ||
      ".idea".equals(file.getParent().getName()) || isRazorFile(file)) {
      return ExcludeResult.excluded("file's type or location are not supported");
    }
    return ExcludeResult.notExcluded();
  }

  public static boolean isRazorFile(VirtualFile file) {
    return file.getExtension() != null && (file.getExtension().equals("razor") || file.getName().endsWith("razor.cs"));
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
    var excludeResultFromEp = ExcludeResult.notExcluded();
    for (var fileExclusion : FileExclusionContributor.EP_NAME.getExtensionList()) {
      excludeResultFromEp = fileExclusion.shouldExclude(m, file);
      if (excludeResultFromEp.isExcluded()) {
        break;
      }
    }
    return excludeResultFromEp;
  }

}
