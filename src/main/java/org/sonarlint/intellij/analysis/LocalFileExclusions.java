/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.project.ExclusionItem;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.common.FileExclusions;

import static org.sonarlint.intellij.config.Settings.getGlobalSettings;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

public class LocalFileExclusions {
  private final ProjectRootManager projectRootManager;
  private final BooleanSupplier powerSaveModeCheck;

  private FileExclusions projectExclusions;
  private FileExclusions globalExclusions;

  public LocalFileExclusions(Project project) {
    this.projectRootManager = ProjectRootManager.getInstance(project);
    this.powerSaveModeCheck = PowerSaveMode::isEnabled;

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
      @Override public void applied(SonarLintGlobalSettings newSettings) {
        loadGlobalExclusions(newSettings);
      }
    });
    busConnection.subscribe(ProjectConfigurationListener.TOPIC, this::loadProjectExclusions);
  }

  /**
   * Checks if a file is excluded from analysis based on locally configured exclusions.
   */
  public Result checkExclusions(VirtualFile file, Module module) {
    Result result = checkFileInSourceFolders(file, module);
    if (result.isExcluded) {
      return result;
    }

    String relativePath = SonarLintAppUtils.getRelativePathForAnalysis(module, file);
    if (relativePath == null) {
      return Result.excluded("Could not create a relative path");
    }
    if (globalExclusions.test(relativePath)) {
      return Result.excluded("file matches exclusions defined in the SonarLint Global Settings");
    }
    if (projectExclusions.test(relativePath)) {
      return Result.excluded("file matches exclusions defined in the SonarLint Project Settings");
    }

    return Result.notExcluded();
  }

  private Result checkFileInSourceFolders(VirtualFile file, Module module) {
    ProjectFileIndex fileIndex = projectRootManager.getFileIndex();

    if (fileIndex.isExcluded(file)) {
      return Result.excluded("file is excluded or ignored in IntelliJ's project structure");
    }

    SourceFolder sourceFolder = SonarLintUtils.getSourceFolder(fileIndex.getSourceRootForFile(file), module);
    if (sourceFolder != null) {
      if (SonarLintUtils.isGeneratedSource(sourceFolder)) {
        return Result.excluded("file is classified as generated in IntelliJ's project structure");
      }
      if (SonarLintUtils.isJavaResource(sourceFolder)) {
        return Result.excluded("file is classified as resource in IntelliJ's project structure");
      }
    }

    // the fact that the file doesn't explicitly belong to sources doesn't mean it's not sources.
    // In WebStorm, for example, everything is considered to be sources unless it is explicitly marked otherwise.
    return Result.notExcluded();
  }

  public Result canAnalyze(VirtualFile file, @Nullable Module module) {
    if (powerSaveModeCheck.getAsBoolean()) {
      return Result.excluded("power save mode is enabled");
    }

    FileType fileType = file.getFileType();
    if (module == null) {
      return Result.excluded("file is not part of any module in IntelliJ's project structure");
    }

    if (module.isDisposed() || module.getProject().isDisposed()) {
      return Result.excluded("module is disposed");
    }

    if (!file.isInLocalFileSystem() || fileType.isBinary() || !file.isValid()
      || ".idea".equals(file.getParent().getName())) {
      return Result.excluded("file's type or location are not supported");
    }

    return Result.notExcluded();
  }

  public static class Result {
    private final boolean isExcluded;
    @Nullable
    private final String excludeReason;

    private Result(boolean isExcluded, @Nullable String excludeReason) {
      this.isExcluded = isExcluded;
      this.excludeReason = excludeReason;
    }

    public boolean isExcluded() {
      return isExcluded;
    }

    @CheckForNull
    public String excludeReason() {
      return excludeReason;
    }

    public static Result excluded(@Nullable String excludeReason) {
      return new Result(true, excludeReason);
    }

    public static Result notExcluded() {
      return new Result(false, null);
    }
  }
}
