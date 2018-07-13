/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.project.ExclusionItem;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarsource.sonarlint.core.client.api.common.FileExclusions;

import static org.sonarlint.intellij.util.SonarLintUtils.isExcludedOrUnderExcludedDirectory;
import static org.sonarlint.intellij.util.SonarLintUtils.isJavaGeneratedSource;
import static org.sonarlint.intellij.util.SonarLintUtils.isJavaResource;

public class LocalFileExclusions {
  private final SonarLintAppUtils appUtils;
  private final ApplicationInfo applicationInfo;
  private FileExclusions projectExclusions;
  private FileExclusions globalExclusions;
  private Supplier<Boolean> powerSaveModeCheck;

  /**
   * Used by pico container
   */
  public LocalFileExclusions(Project project, SonarLintGlobalSettings settings, SonarLintProjectSettings projectSettings, SonarLintAppUtils appUtils,
    ApplicationInfo applicationInfo) {
    this(project, settings, projectSettings, appUtils, applicationInfo, PowerSaveMode::isEnabled);
  }

  public LocalFileExclusions(Project project, SonarLintGlobalSettings settings, SonarLintProjectSettings projectSettings, SonarLintAppUtils appUtils,
    ApplicationInfo applicationInfo, Supplier<Boolean> powerSaveModeCheck) {
    this.appUtils = appUtils;
    this.applicationInfo = applicationInfo;
    loadGlobalExclusions(settings);
    loadProjectExclusions(projectSettings);
    subscribeToSettingsChanges(project);
    this.powerSaveModeCheck = powerSaveModeCheck;
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
   * It will also exclude files that cannot be analysed with {@link #canAnalyze(VirtualFile, Module)}.
   */
  public Result checkExclusions(VirtualFile file, @Nullable Module module) {
    Result result = canAnalyze(file, module);
    if (result.isExcluded()) {
      return result;
    }

    if (powerSaveModeCheck.get()) {
      return Result.excluded("power save mode is enabled");
    }

    Result r = checkFileInSourceFolders(file, module);
    if (r.isExcluded) {
      return r;
    }

    String relativePath = appUtils.getRelativePathForAnalysis(module, file);
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

  private static Result checkFileInSourceFolders(VirtualFile file, Module module) {
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    ContentEntry[] entries = moduleRootManager.getContentEntries();

    for (ContentEntry e : entries) {
      if (isExcludedOrUnderExcludedDirectory(file, e)) {
        return Result.excluded("file is excluded in Project Structure");
      }

      SourceFolder[] sourceFolders = e.getSourceFolders();

      for (SourceFolder sourceFolder : sourceFolders) {
        if (sourceFolder.getFile() == null || sourceFolder.isSynthetic()) {
          continue;
        }

        if (VfsUtil.isAncestor(sourceFolder.getFile(), file, false)) {
          return checkSourceFolder(sourceFolder);
        }
      }
    }

    // java must be in a source root. For other files, we always analyze them.
    if ("java".equalsIgnoreCase(file.getFileType().getDefaultExtension())) {
      return Result.excluded("java file not under a source root");
    }
    return Result.notExcluded();
  }

  private static Result checkSourceFolder(SourceFolder sourceFolder) {
    if (isJavaResource(sourceFolder)) {
      Result.excluded("file is under a resource folder");
    } else if (isJavaGeneratedSource(sourceFolder)) {
      Result.excluded("file belongs to generated source folder");
    }

    return Result.notExcluded();
  }

  public Result canAnalyze(VirtualFile file, @Nullable Module module) {
    if (module == null) {
      return Result.excluded("file is not part of any module in IntelliJ's project structure");
    }

    if (module.isDisposed() || module.getProject().isDisposed()) {
      return Result.excluded("module is disposed");
    }

    if (!file.isInLocalFileSystem() || file.getFileType().isBinary() || !file.isValid()
      || ".idea".equals(file.getParent().getName())) {
      return Result.excluded("file's type or location are not supported");
    }

    // In PHPStorm the same PHP file is analyzed twice (once as PHP file and once as HTML file)
    String ijFlavor = applicationInfo.getVersionName().toLowerCase(Locale.US);
    if (ijFlavor.contains("phpstorm") && "html".equalsIgnoreCase(file.getFileType().getName())) {
      return Result.excluded(null);
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
