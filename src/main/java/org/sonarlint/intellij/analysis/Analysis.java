/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.core.ServerIssueUpdater;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.finding.LiveFinding;
import org.sonarlint.intellij.finding.LiveFindings;
import org.sonarlint.intellij.finding.hotspot.ServerSecurityHotspotUpdater;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.finding.persistence.FindingsCache;
import org.sonarlint.intellij.messages.AnalysisListener;
import org.sonarlint.intellij.telemetry.SonarLintTelemetry;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.util.TaskProgressMonitor;
import org.sonarsource.sonarlint.core.commons.progress.CanceledException;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.common.util.SonarLintUtils.pluralize;

public class Analysis implements Cancelable {
  private final Project project;
  private final Collection<VirtualFile> files;
  private final TriggerType trigger;
  private final boolean waitForServerFindings;
  private final AnalysisCallback callback;
  private boolean finished = false;
  private boolean cancelled;

  public Analysis(Project project, Collection<VirtualFile> files, TriggerType trigger, boolean waitForServerFindings,
    AnalysisCallback callback) {
    this.project = project;
    this.files = files;
    this.trigger = trigger;
    this.waitForServerFindings = waitForServerFindings;
    this.callback = callback;
  }


  public AnalysisResult run(ProgressIndicator indicator) {
    try {
      notifyStart();
      return doRun(indicator);
    } finally {
      finished = true;
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
    }
  }

  private void notifyStart() {
    project.getMessageBus().syncPublisher(AnalysisListener.TOPIC).started(files);
  }

  private AnalysisResult doRun(ProgressIndicator indicator) {
    var console = getService(project, SonarLintConsole.class);
    console.debug("Trigger: " + trigger);
    console.debug(String.format("[%s] %d file(s) submitted", trigger, files.size()));

    AnalysisScope scope;
    try {
      scope = AnalysisScope.defineFrom(project, files, trigger);
    } catch (InvalidBindingException e) {
      // nothing to do, SonarLintEngineManager already showed notification
      return new AnalysisResult(LiveFindings.none(), files, trigger, Instant.now());
    }

    var findingsCache = getService(project, FindingsCache.class);

    try {
      checkCanceled(indicator);

      var previousFindings = findingsCache.clearFindings(files);

      if (scope.isEmpty()) {
        var analysisResult = new AnalysisResult(LiveFindings.none(), files, trigger, Instant.now());
        callback.onSuccess(analysisResult);
        return analysisResult;
      }
      var findingStreamer = new FindingStreamer(callback);
      var rawFindingHandler = new RawFindingHandler(project, findingStreamer, findingsCache, previousFindings);
      var summary = analyzePerModule(scope, indicator, rawFindingHandler);
      findingStreamer.stopStreaming();

      getService(SonarLintTelemetry.class).addReportedRules(summary.getReportedRuleKeys());

      indicator.setIndeterminate(false);
      indicator.setFraction(.9);

      summary.logInConsole();

      matchWithServerIssuesIfNeeded(indicator, summary.filesHavingIssuesByModule);
      matchWithServerSecurityHotspotsIfNeeded(indicator, summary.filesHavingSecurityHotspotsByModule);

      var result = new AnalysisResult(summary.findings, files, trigger, Instant.now());
      callback.onSuccess(result);
      return result;
    } catch (CanceledException | ProcessCanceledException e1) {
      console.info("Analysis canceled");
    } catch (Throwable e) {
      handleError(e, indicator);
    }
    return new AnalysisResult(LiveFindings.none(), files, trigger, Instant.now());
  }

  private void matchWithServerIssuesIfNeeded(ProgressIndicator indicator, Map<Module, Collection<VirtualFile>> filesHavingIssuesByModule) {
    if (!filesHavingIssuesByModule.isEmpty()) {
      var serverIssueUpdater = SonarLintUtils.getService(project, ServerIssueUpdater.class);
      if (shouldUpdateServerIssues(trigger)) {
        serverIssueUpdater.fetchAndMatchServerIssues(filesHavingIssuesByModule, indicator, waitForServerFindings);
      } else {
        serverIssueUpdater.matchServerIssues(filesHavingIssuesByModule);
      }
    }
  }

  private void matchWithServerSecurityHotspotsIfNeeded(ProgressIndicator indicator,
    Map<Module, Collection<VirtualFile>> filesHavingSecurityHotspotsByModule) {
    if (!filesHavingSecurityHotspotsByModule.isEmpty()) {
      var updater = SonarLintUtils.getService(project, ServerSecurityHotspotUpdater.class);
      if (shouldUpdateServerIssues(trigger)) {
        updater.fetchAndMatchServerSecurityHotspots(filesHavingSecurityHotspotsByModule, indicator, waitForServerFindings);
      } else {
        updater.matchServerSecurityHotspots(filesHavingSecurityHotspotsByModule);
      }
    }
  }

  private static boolean shouldUpdateServerIssues(TriggerType trigger) {
    switch (trigger) {
      case ACTION:
      case CONFIG_CHANGE:
      case BINDING_UPDATE:
      case CHECK_IN:
      case EDITOR_OPEN:
        return true;
      default:
        return false;
    }
  }

  private void handleError(Throwable e, ProgressIndicator indicator) {
    // if cancelled, ignore any errors since they were most likely caused by the interrupt
    if (!isCancelled(indicator)) {
      var message = "Error running SonarLint analysis";
      var console = SonarLintConsole.get(project);
      console.error(message, e);

      if (indicator.isShowing()) {
        var dialogMsg = "SonarLint analysis failed: " + e.getMessage();
        ApplicationManager.getApplication().invokeAndWait(() -> Messages.showErrorDialog(dialogMsg, "Error Running SonarLint Analysis"),
          ModalityState.defaultModalityState());
      }

      callback.onError(e);
    }
  }

  private void checkCanceled(ProgressIndicator indicator) {
    if (isCancelled(indicator)) {
      throw new CanceledException();
    }
  }

  private boolean isCancelled(ProgressIndicator indicator) {
    return cancelled || indicator.isCanceled() || project.isDisposed() || Thread.currentThread().isInterrupted() || AnalysisStatus.get(project).isCanceled();
  }

  private Summary analyzePerModule(AnalysisScope scope, ProgressIndicator indicator, RawFindingHandler rawFindingHandler) {
    indicator.setIndeterminate(true);
    indicator.setText("Running SonarLint Analysis for " + scope.getDescription());

    var analyzer = getService(project, SonarLintAnalyzer.class);
    var progressMonitor = new TaskProgressMonitor(indicator, project, () -> cancelled);
    var results = new LinkedHashMap<Module, ModuleAnalysisResult>();

    for (var entry : scope.getFilesByModule().entrySet()) {
      var module = entry.getKey();
      results.put(module, analyzer.analyzeModule(module, entry.getValue(), rawFindingHandler, progressMonitor));
      checkCanceled(indicator);
    }
    return summarize(scope, rawFindingHandler, results);
  }

  private Summary summarize(AnalysisScope scope, RawFindingHandler rawFindingHandler, Map<Module, ModuleAnalysisResult> resultsByModule) {
    var allFailedFiles = resultsByModule.values().stream().flatMap(r -> r.failedFiles().stream()).collect(toSet());
    return new Summary(project, scope.getFilesByModule(), allFailedFiles, rawFindingHandler.getRawIssueCount(),
      new LiveFindings(rawFindingHandler.getIssuesPerFile(), rawFindingHandler.getSecurityHotspotsPerFile()));
  }

  private static class Summary {
    private final Project project;
    private final Set<VirtualFile> failedFiles;
    private final int rawIssueCount;
    private final LiveFindings findings;
    private final Set<String> reportedRuleKeys = new HashSet<>();
    private final long issuesCount;
    private final long securityHotspotsCount;
    private final boolean onlyFailedFiles;
    private final Map<Module, Collection<VirtualFile>> filesHavingIssuesByModule;
    private final Map<Module, Collection<VirtualFile>> filesHavingSecurityHotspotsByModule;

    public Summary(Project project, Map<Module, Collection<VirtualFile>> filesByModule, Set<VirtualFile> failedFiles, int rawIssueCount,
      LiveFindings findings) {
      this.project = project;
      this.failedFiles = failedFiles;
      this.rawIssueCount = rawIssueCount;
      this.findings = findings;
      this.reportedRuleKeys.addAll(findings.getIssuesPerFile().values().stream().flatMap(issues -> issues.stream().map(LiveIssue::getRuleKey)).collect(Collectors.toSet()));
      this.reportedRuleKeys.addAll(findings.getSecurityHotspotsPerFile().values().stream()
        .flatMap(hotspots -> hotspots.stream().map(LiveFinding::getRuleKey)).collect(Collectors.toSet()));
      this.filesHavingIssuesByModule = filterFilesHavingFindingsByModule(filesByModule, findings.getIssuesPerFile());
      this.filesHavingSecurityHotspotsByModule = filterFilesHavingFindingsByModule(filesByModule, findings.getSecurityHotspotsPerFile());
      this.securityHotspotsCount = findings.getSecurityHotspotsPerFile().values().stream().mapToLong(Collection::size).sum();
      this.issuesCount = findings.getSecurityHotspotsPerFile().values().stream().mapToLong(Collection::size).sum();
      this.onlyFailedFiles = failedFiles.containsAll(filesByModule.values().stream().flatMap(Collection::stream).collect(toSet()));
    }

    private static <L extends LiveFinding> Map<Module, Collection<VirtualFile>> filterFilesHavingFindingsByModule(Map<Module,
      Collection<VirtualFile>> filesByModule, Map<VirtualFile, Collection<L>> issuesPerFile) {
      var filesWithIssuesPerModule = new LinkedHashMap<Module, Collection<VirtualFile>>();

      for (var entry : filesByModule.entrySet()) {
        var moduleFilesWithIssues =
          entry.getValue().stream().filter(f -> !issuesPerFile.getOrDefault(f, Collections.emptyList()).isEmpty()).collect(toList());
        if (!moduleFilesWithIssues.isEmpty()) {
          filesWithIssuesPerModule.put(entry.getKey(), moduleFilesWithIssues);
        }
      }
      return filesWithIssuesPerModule;
    }

    public Set<String> getReportedRuleKeys() {
      return reportedRuleKeys;
    }

    private void logInConsole() {
      logFailedFiles();
      if (!onlyFailedFiles) {
        logFoundIssues();
      }
    }

    private void logFailedFiles() {
      failedFiles.forEach(vFile -> SonarLintConsole.get(project).debug("Analysis of file '" + vFile.getPath() + "' might not be " +
        "accurate because there were errors" + " during analysis"));
    }

    private void logFoundIssues() {
      var issueStr = pluralize("issue", rawIssueCount);
      var console = SonarLintConsole.get(project);
      console.debug(String.format("Processed %d %s", rawIssueCount, issueStr));

      var issuesText = pluralize("issue", issuesCount);
      var hotspotsText = pluralize("hotspot", securityHotspotsCount);
      console.info("Found " + issuesCount + " " + issuesText + " and " + securityHotspotsCount + " " + hotspotsText);
    }
  }

}
