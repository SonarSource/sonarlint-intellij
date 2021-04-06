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
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.analysis.LocalFileExclusions;
import org.sonarlint.intellij.analysis.SonarLintJobManager;
import org.sonarlint.intellij.analysis.SonarLintTask;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.exception.InvalidBindingException;
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
    // If user explicitly ask to analyze a single file, we should ignore certains exclusions
    boolean forcedAnalysis = trigger == TriggerType.ACTION;
    try {
      List<VirtualFile> filesToClearIssues = new ArrayList<>();
      Map<Module, Collection<VirtualFile>> filesByModule = filterAndGetByModule(files, forcedAnalysis, filesToClearIssues);

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

  private Map<Module, Collection<VirtualFile>> filterAndGetByModule(Collection<VirtualFile> files, boolean forcedAnalysis, List<VirtualFile> filesToClearIssues)
    throws InvalidBindingException {
    LocalFileExclusions localFileExclusions = SonarLintUtils.getService(myProject, LocalFileExclusions.class);
    return localFileExclusions.retainNonExcludedFilesByModules(files, forcedAnalysis, (f, r) -> logExclusionAndAddToClearList(filesToClearIssues, f, r.excludeReason()));
  }

  private void logExclusionAndAddToClearList(List<VirtualFile> filesToClearIssues, VirtualFile file, String s) {
    logExclusion(file, s);
    filesToClearIssues.add(file);
  }

  private void logExclusion(VirtualFile f, String reason) {
    SonarLintConsole console = SonarLintUtils.getService(myProject, SonarLintConsole.class);
    console.debug("File '" + f.getName() + "' excluded: " + reason);
  }
}
