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

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.CheckForNull;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.callable.CheckInCallable;
import org.sonarlint.intellij.callable.ShowFindingCallable;
import org.sonarlint.intellij.callable.ShowReportCallable;
import org.sonarlint.intellij.callable.ShowUpdatedCurrentFileCallable;
import org.sonarlint.intellij.callable.UpdateOnTheFlyFindingsCallable;
import org.sonarlint.intellij.common.analysis.AnalysisConfigurator;
import org.sonarlint.intellij.common.analysis.ForcedLanguage;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.finding.Finding;
import org.sonarlint.intellij.finding.ShowFinding;
import org.sonarlint.intellij.tasks.TaskRunnerKt;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.config.Settings.getGlobalSettings;
import static org.sonarlint.intellij.util.SonarLintAppUtils.findModuleForFile;
import static org.sonarlint.intellij.util.SonarLintAppUtils.visitAndAddAllFilesForProject;

@Service(Service.Level.PROJECT)
public final class AnalysisSubmitter {
  public static final String ANALYSIS_TASK_TITLE = "SonarQube for IDE Analysis";
  private final Project project;
  private final OnTheFlyFindingsHolder onTheFlyFindingsHolder;
  private Cancelable currentManualAnalysis;

  public AnalysisSubmitter(Project project) {
    this.project = project;
    this.onTheFlyFindingsHolder = new OnTheFlyFindingsHolder(project);
  }

  public OnTheFlyFindingsHolder getOnTheFlyFindingsHolder() {
    return onTheFlyFindingsHolder;
  }

  public void cancelCurrentManualAnalysis() {
    if (currentManualAnalysis != null) {
      currentManualAnalysis.cancel();
      currentManualAnalysis = null;
    }
  }

  public void analyzeAllFiles() {
    var allFiles = visitAndAddAllFilesForProject(project);
    var callback = new ShowReportCallable(project);
    var analysis = new Analysis(project, allFiles, TriggerType.ALL, callback);
    TaskRunnerKt.startBackgroundableModalTask(project, ANALYSIS_TASK_TITLE, analysis::run);
  }

  public void analyzeVcsChangedFiles() {
    var changedFiles = ChangeListManager.getInstance(project).getAffectedFiles();
    var callback = new ShowReportCallable(project);
    var analysis = new Analysis(project, changedFiles, TriggerType.CHANGED_FILES, callback);
    TaskRunnerKt.startBackgroundableModalTask(project, ANALYSIS_TASK_TITLE, analysis::run);
  }

  public void autoAnalyzeSelectedFilesForModule(TriggerType triggerType, @Nullable Module module) {
    if (module == null) {
      autoAnalyzeSelectedFiles(triggerType);
      return;
    }

    var selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
    var filesToAnalyze = Arrays.stream(selectedFiles)
      .filter(file -> module.equals(findModuleForFile(file, project)))
      .toList();

    if (!filesToAnalyze.isEmpty()) {
      autoAnalyzeFiles(filesToAnalyze, triggerType);
    }
  }

  public void autoAnalyzeSelectedFiles(TriggerType triggerType) {
    var selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();

    if (selectedFiles.length > 0) {
      autoAnalyzeFiles(List.of(selectedFiles), triggerType);
    }
  }

  @CheckForNull
  public Cancelable autoAnalyzeFiles(Collection<VirtualFile> files, TriggerType triggerType) {
    if (!getGlobalSettings().isAutoTrigger()) {
      return null;
    }
    var callback = new UpdateOnTheFlyFindingsCallable(onTheFlyFindingsHolder);
    if (triggerType != TriggerType.SELECTION_CHANGED) {
      onTheFlyFindingsHolder.clearNonDirtyAnalyzedFiles();
    }
    return analyzeInBackground(files, triggerType, callback);
  }

  @CheckForNull
  public Pair<CheckInCallable, List<UUID>> analyzeFilesPreCommit(Collection<VirtualFile> files) {
    var console = SonarLintUtils.getService(project, SonarLintConsole.class);
    var trigger = TriggerType.CHECK_IN;
    console.debug("Trigger: " + trigger);
    if (shouldSkipAnalysis()) {
      return null;
    }

    var callback = new CheckInCallable();
    var analysis = new Analysis(project, files, trigger, callback);
    var analysisIds = TaskRunnerKt.runModalTaskWithResult(project, ANALYSIS_TASK_TITLE, analysis::run);
    return Pair.of(callback, analysisIds);
  }

  public void analyzeFilesOnUserAction(Collection<VirtualFile> files, AnActionEvent actionEvent) {
    AnalysisCallback callback;
    TriggerType triggerType;

    if (SonarLintToolWindowFactory.TOOL_WINDOW_ID.equals(actionEvent.getPlace())) {
      callback = new ShowUpdatedCurrentFileCallable(project, onTheFlyFindingsHolder);
      triggerType = TriggerType.CURRENT_FILE_ACTION;
    } else {
      callback = new ShowReportCallable(project);
      triggerType = TriggerType.RIGHT_CLICK;
    }

    // do we really need to distinguish both cases ? Couldn't we always run in background ?
    if (shouldExecuteInBackground(actionEvent)) {
      analyzeInBackground(files, triggerType, callback);
    } else {
      currentManualAnalysis = analyzeInBackgroundableModal(files, triggerType, callback);
    }
  }

  public <T extends Finding> void analyzeFileAndTrySelectFinding(ShowFinding<T> showFinding) {
    getService(project, OpenInIdeFindingCache.class).setAnalysisQueued(true);
    AnalysisCallback callback = new ShowFindingCallable<>(project, onTheFlyFindingsHolder, showFinding);
    var task = new Analysis(project, List.of(showFinding.getFile()), TriggerType.OPEN_FINDING, callback);
    TaskRunnerKt.startBackgroundableModalTask(project, ANALYSIS_TASK_TITLE, task::run);
  }

  /**
   * Whether the analysis should be launched in the background.
   * Analysis should be run in background in the following cases:
   * - Keybinding used (place = MainMenu)
   * - Macro used (place = unknown)
   * - Action used, ctrl+shift+A (place = GoToAction)
   */
  private static boolean shouldExecuteInBackground(AnActionEvent e) {
    return ActionPlaces.isMainMenuOrActionSearch(e.getPlace())
      || ActionPlaces.UNKNOWN.equals(e.getPlace());
  }

  public static Map<VirtualFile, ForcedLanguage> collectContributedLanguages(Module module, Collection<VirtualFile> listFiles) {
    var contributedConfigurations = AnalysisConfigurator.EP_NAME.getExtensionList().stream()
      .map(config -> config.configure(module, listFiles)).toList();

    var contributedLanguages = new HashMap<VirtualFile, ForcedLanguage>();
    for (var config : contributedConfigurations) {
      contributedLanguages.putAll(config.forcedLanguages);
    }
    return contributedLanguages;
  }

  private Cancelable analyzeInBackground(Collection<VirtualFile> files, TriggerType trigger, AnalysisCallback callback) {
    var analysis = new Analysis(project, files, trigger, callback);
    TaskRunnerKt.startBackgroundTask(project, ANALYSIS_TASK_TITLE, analysis::run);
    return analysis;
  }

  private Cancelable analyzeInBackgroundableModal(Collection<VirtualFile> files, TriggerType action, AnalysisCallback callback) {
    if (shouldSkipAnalysis()) {
      return null;
    }
    var analysis = new Analysis(project, files, action, callback);
    TaskRunnerKt.startBackgroundableModalTask(project, ANALYSIS_TASK_TITLE, analysis::run);
    return analysis;
  }

  private boolean shouldSkipAnalysis() {
    var status = SonarLintUtils.getService(project, AnalysisStatus.class);
    var console = SonarLintUtils.getService(project, SonarLintConsole.class);
    if (project.isDisposed() || !status.tryRun()) {
      console.info("Canceling analysis triggered by the user because another one is already running or because the project is disposed");
      return true;
    }
    return false;
  }

}
