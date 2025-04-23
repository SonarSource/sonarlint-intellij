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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.sonarlint.intellij.cayc.NewCodePeriodCache;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.fs.VirtualFileEvent;
import org.sonarlint.intellij.messages.AnalysisListener;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarsource.sonarlint.plugin.api.module.file.ModuleFileEvent;

import static java.util.stream.Collectors.toSet;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.common.util.SonarLintUtils.isRider;
import static org.sonarlint.intellij.ui.UiUtils.runOnUiThreadAndWait;

public class Analysis implements Cancelable {
  private final Project project;
  private final Collection<VirtualFile> files;
  private final TriggerType trigger;
  private final AnalysisCallback callback;
  private boolean finished = false;
  private boolean cancelled;
  private ProgressIndicator indicator;

  public Analysis(Project project, Collection<VirtualFile> files, TriggerType trigger, AnalysisCallback callback) {
    this.project = project;
    this.files = files;
    this.trigger = trigger;
    this.callback = callback;
  }

  public List<UUID> run(ProgressIndicator indicator) {
    try {
      finished = false;
      this.indicator = indicator;
      notifyStart();
      return doRun(indicator);
    } finally {
      finished = true;
      this.indicator = null;
      if (!project.isDisposed()) {
        getService(project, AnalysisStatus.class).stopRun();
      }
    }
  }

  public boolean isFinished() {
    return finished;
  }

  @Override
  public void cancel() {
    if (!isFinished()) {
      this.cancelled = true;
      if (indicator != null) {
        indicator.cancel();
      }
    }
  }

  private void notifyStart() {
    project.getMessageBus().syncPublisher(AnalysisListener.TOPIC).started(files, trigger);
  }

  private List<UUID> doRun(ProgressIndicator indicator) {
    var console = getService(project, SonarLintConsole.class);
    console.debug("Trigger: " + trigger);
    console.debug(String.format("[%s] %d file(s) submitted", trigger, files.size()));
    if (!getService(BackendService.class).isAlive()) {
      console.info("Analysis skipped as SonarQube for IDE is not alive");
      return Collections.emptyList();
    }
    if (!getService(project, AnalysisReadinessCache.class).isProjectReady()) {
      if (trigger == TriggerType.OPEN_FINDING) {
        getService(project, OpenInIdeFindingCache.class).setAnalysisQueued(false);
      }
      console.info("Analysis skipped as the engine is not ready yet");
      return Collections.emptyList();
    }

    if (trigger == TriggerType.OPEN_FINDING) {
      getService(project, OpenInIdeFindingCache.class).setFinding(null);
      getService(project, OpenInIdeFindingCache.class).setAnalysisQueued(false);
    }

    var scope = AnalysisScope.defineFrom(project, files, trigger);

    // refresh should ideally not be done here, see SLCORE-729
    getService(project, NewCodePeriodCache.class).refreshAsync();

    try {
      checkCanceled(indicator);

      if (scope.isEmpty()) {
        return Collections.emptyList();
      }

      var summary = analyzePerModule(scope, indicator, trigger);

      indicator.setIndeterminate(false);
      indicator.setFraction(.9);

      summary.logFailedFiles();

      checkCanceled(indicator);
      checkCanceled(indicator);
      return summary.analysisIds;
    } catch (ProcessCanceledException e) {
      console.info("Analysis canceled");
    } catch (Exception e) {
      handleError(e, indicator);
    }
    return Collections.emptyList();
  }

  private void handleError(Throwable e, ProgressIndicator indicator) {
    // if cancelled, ignore any errors since they were most likely caused by the interrupt
    if (!isCancelled(indicator)) {
      var message = "Error running SonarQube for IDE analysis";
      var console = SonarLintConsole.get(project);
      console.error(message, e);

      if (indicator.isShowing()) {
        var dialogMsg = "SonarQube for IDE analysis failed: " + e.getMessage();
        runOnUiThreadAndWait(project, indicator.getModalityState(), () -> Messages.showErrorDialog(dialogMsg, "Error Running SonarQube for IDE Analysis"));
      }

      callback.onError(e);
    }
  }

  private void checkCanceled(ProgressIndicator indicator) {
    if (isCancelled(indicator)) {
      throw new ProcessCanceledException();
    }
  }

  private boolean isCancelled(ProgressIndicator indicator) {
    return cancelled || indicator.isCanceled() || project.isDisposed() || Thread.currentThread().isInterrupted() || AnalysisStatus.get(project).isCanceled();
  }

  private Summary analyzePerModule(AnalysisScope scope, ProgressIndicator indicator, TriggerType trigger) {
    indicator.setIndeterminate(true);
    indicator.setText("Running SonarQube for IDE Analysis for " + scope.getDescription());

    var analyzer = getService(project, SonarLintAnalyzer.class);
    var results = new LinkedHashMap<Module, ModuleAnalysisResult>();
    var analysisIds = new ArrayList<UUID>();
    for (var entry : scope.getFilesByModule().entrySet()) {
      var module = entry.getKey();
      var analysisId = UUID.randomUUID();
      analysisIds.add(analysisId);

      if (isRider()) {
        var filesEvent = entry.getValue().stream().map(file -> new VirtualFileEvent(ModuleFileEvent.Type.CREATED, file)).toList();
        getService(BackendService.class).updateFileSystem(Map.of(module, filesEvent), true);
      }

      var analysisState = new AnalysisState(analysisId, callback, entry.getValue(), module, trigger, indicator);
      results.put(module, analyzer.analyzeModule(module, entry.getValue(), analysisState, indicator, scope.shouldFetchServerIssues()));
      checkCanceled(indicator);
    }
    return summarize(results, analysisIds);
  }

  private Summary summarize(Map<Module, ModuleAnalysisResult> resultsByModule, List<UUID> analysisIds) {
    var allFailedFiles = resultsByModule.values().stream().flatMap(r -> r.failedFiles().stream()).collect(toSet());
    return new Summary(project, allFailedFiles, analysisIds);
  }

  private record Summary(Project project, Set<VirtualFile> failedFiles, List<UUID> analysisIds) {

    public void logFailedFiles() {
      failedFiles.forEach(vFile -> SonarLintConsole.get(project).debug("Analysis of file '" + vFile.getPath() + "' might not be " +
        "accurate because there were errors" + " during analysis"));
    }

  }

}
