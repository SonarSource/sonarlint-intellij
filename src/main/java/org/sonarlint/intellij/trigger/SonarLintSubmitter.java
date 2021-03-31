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
package org.sonarlint.intellij.trigger;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.CheckForNull;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.analysis.LocalFileExclusions;
import org.sonarlint.intellij.analysis.SonarLintJobManager;
import org.sonarlint.intellij.analysis.SonarLintTask;
import org.sonarlint.intellij.common.analysis.ExcludeResult;
import org.sonarlint.intellij.common.analysis.FileExclusionContributor;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.core.SonarLintFacade;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarlint.intellij.util.SonarLintUtils;

import static org.sonarlint.intellij.config.Settings.getGlobalSettings;

public class SonarLintSubmitter {
  static final AnalysisCallback NO_OP_CALLBACK = new AnalysisCallback() {
    @Override
    public void onSuccess(Set<VirtualFile> failedVirtualFiles) {
      // Ignore
    }

    @Override
    public void onError(Throwable e) {
      // Ignore
    }
  };
  private final Project myProject;

  public SonarLintSubmitter(Project project) {
    this.myProject = project;
  }

  public void submitOpenFilesAuto(TriggerType trigger) {
    if (!getGlobalSettings().isAutoTrigger()) {
      return;
    }
    FileEditorManager editorManager = FileEditorManager.getInstance(myProject);
    VirtualFile[] openFiles = editorManager.getOpenFiles();
    submitFiles(Arrays.asList(openFiles), trigger, true);
  }

  /**
   * Submit files for analysis.
   * This is a user-initiated action. It will start running in foreground, modal (blocking) and will consume the single slot, updating
   * the status of the associated actions / icons.
   *
   * @param files   Files to be analyzed
   * @param trigger What triggered the analysis
   */
  public void submitFilesModal(Collection<VirtualFile> files, TriggerType trigger, AnalysisCallback callback) {
    try {
      List<VirtualFile> filesToClearIssues = new ArrayList<>();
      Map<Module, Collection<VirtualFile>> filesByModule = filterAndGetByModule(files, false, filesToClearIssues);
      if (!files.isEmpty()) {
        SonarLintConsole console = SonarLintUtils.getService(myProject, SonarLintConsole.class);
        console.debug("Trigger: " + trigger);
        SonarLintJobManager sonarLintJobManager = SonarLintUtils.getService(myProject, SonarLintJobManager.class);
        sonarLintJobManager.submitManual(filesByModule, filesToClearIssues, trigger, true, callback);
      }
    } catch (InvalidBindingException e) {
      // nothing to do, SonarLintEngineManager already showed notification
    }
  }

  /**
   * Submit files for analysis.
   *
   * @param files             Files to be analyzed.
   * @param trigger           What triggered the analysis
   * @param startInBackground Whether the analysis was triggered automatically. It affects the filter of files that should be analyzed and also
   *                          if it starts in background or foreground.
   */
  @CheckForNull
  public SonarLintTask submitFiles(Collection<VirtualFile> files, TriggerType trigger, boolean startInBackground) {
    return submitFiles(files, trigger, NO_OP_CALLBACK, startInBackground);
  }

  @CheckForNull
  public SonarLintTask submitFiles(Collection<VirtualFile> files, TriggerType trigger, AnalysisCallback callback, boolean startInBackground) {
    // If user explicitly ask to analyze a single file, we should ignore configured user exclusions
    boolean checkUserExclusions = trigger != TriggerType.ACTION;
    try {
      List<VirtualFile> filesToClearIssues = new ArrayList<>();
      Map<Module, Collection<VirtualFile>> filesByModule = filterAndGetByModule(files, checkUserExclusions, filesToClearIssues);

      if (!files.isEmpty()) {
        SonarLintConsole console = SonarLintUtils.getService(myProject, SonarLintConsole.class);
        console.debug("Trigger: " + trigger);
        SonarLintJobManager sonarLintJobManager = SonarLintUtils.getService(myProject, SonarLintJobManager.class);
        if (startInBackground) {
          return sonarLintJobManager.submitBackground(filesByModule, filesToClearIssues, trigger, callback);
        } else {
          return sonarLintJobManager.submitManual(filesByModule, filesToClearIssues, trigger, false, callback);
        }
      }
    } catch (InvalidBindingException e) {
      // nothing to do, SonarLintEngineManager already showed notification
    }
    return null;
  }

  private Map<Module, Collection<VirtualFile>> filterAndGetByModule(Collection<VirtualFile> files, boolean checkUserExclusions, List<VirtualFile> filesToClearIssues)
    throws InvalidBindingException {
    Map<Module, Collection<VirtualFile>> filesByModule = new LinkedHashMap<>();

    for (VirtualFile file : files) {
      checkExclusionsFileByFile(checkUserExclusions, filesToClearIssues, filesByModule, file);
    }
    if (checkUserExclusions) {
      // Apply server file exclusions. This is an expensive operation, so we call the core only once per module.
      filterWithServerExclusions(filesToClearIssues, filesByModule);
    }

    return filesByModule;
  }

  private void checkExclusionsFileByFile(boolean checkUserExclusions, List<VirtualFile> filesToClearIssues, Map<Module, Collection<VirtualFile>> filesByModule, VirtualFile file) {
    LocalFileExclusions localFileExclusions = SonarLintUtils.getService(myProject, LocalFileExclusions.class);
    Module m = SonarLintAppUtils.findModuleForFile(file, myProject);
    ExcludeResult result = localFileExclusions.canAnalyze(file, m);
    if (result.isExcluded()) {
      logExclusionAndAddToClearList(filesToClearIssues, file, result.excludeReason());
      return;
    }
    // here module is not null or file would have been already excluded by canAnalyze
    ExcludeResult excludeResultFromEp = checkExclusionFromEP(file, m);
    if (excludeResultFromEp.isExcluded()) {
      logExclusionAndAddToClearList(filesToClearIssues, file, excludeResultFromEp.excludeReason());
      return;
    }
    if (checkUserExclusions) {
      result = localFileExclusions.checkExclusions(file, m);
      if (result.isExcluded()) {
        logExclusionAndAddToClearList(filesToClearIssues, file, result.excludeReason());
        return;
      }
    }

    filesByModule.computeIfAbsent(m, mod -> new LinkedHashSet<>()).add(file);
  }

  private void logExclusionAndAddToClearList(List<VirtualFile> filesToClearIssues, VirtualFile file, String s) {
    logExclusion(file, s);
    filesToClearIssues.add(file);
  }

  @NotNull
  private ExcludeResult checkExclusionFromEP(VirtualFile file, Module m) {
    ExcludeResult excludeResultFromEp = ExcludeResult.notExcluded();
    for (FileExclusionContributor fileExclusion : FileExclusionContributor.EP_NAME.getExtensionList()) {
      excludeResultFromEp = fileExclusion.shouldExclude(m, file);
      if (excludeResultFromEp.isExcluded()) {
        break;
      }
    }
    return excludeResultFromEp;
  }

  private void filterWithServerExclusions(List<VirtualFile> filesToClearIssues, Map<Module, Collection<VirtualFile>> filesByModule)
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
        logExclusionAndAddToClearList(filesToClearIssues, f, "exclusions configured in the bound project");
      }
      virtualFiles.removeAll(excluded);
      if (virtualFiles.isEmpty()) {
        filesByModule.remove(module);
      }
    }
  }

  private void logExclusion(VirtualFile f, String reason) {
    SonarLintConsole console = SonarLintUtils.getService(myProject, SonarLintConsole.class);
    console.debug("File '" + f.getName() + "' excluded: " + reason);
  }
}
