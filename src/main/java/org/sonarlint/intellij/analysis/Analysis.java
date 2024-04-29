/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonarlint.intellij.cayc.NewCodePeriodCache;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.finding.LiveFinding;
import org.sonarlint.intellij.finding.LiveFindings;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.finding.persistence.CachedFindings;
import org.sonarlint.intellij.finding.persistence.FindingsCache;
import org.sonarlint.intellij.messages.AnalysisListener;
import org.sonarlint.intellij.telemetry.SonarLintTelemetry;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.util.TaskProgressMonitor;
import org.sonarsource.sonarlint.core.commons.api.progress.CanceledException;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.common.util.SonarLintUtils.pluralize;
import static org.sonarlint.intellij.ui.UiUtils.runOnUiThreadAndWait;

public class Analysis implements Cancelable {
  private final Project project;
  private final Collection<VirtualFile> files;
  private final TriggerType trigger;
  private final AnalysisCallback callback;
  private boolean finished = false;
  private boolean cancelled;
  private ProgressIndicator indicator;

  private static final Logger LOGGER = Logger.getInstance(Analysis.class);

  public Analysis(Project project, Collection<VirtualFile> files, TriggerType trigger, AnalysisCallback callback) {
    this.project = project;
    this.files = files;
    this.trigger = trigger;
    this.callback = callback;
  }

  public AnalysisResult run(ProgressIndicator indicator) {
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

  private AnalysisResult doRun(ProgressIndicator indicator) {
    System.out.println("came inside doRun");
    var console = getService(project, SonarLintConsole.class);
    console.debug("Trigger: " + trigger);
    console.debug(String.format("[%s] %d file(s) submitted", trigger, files.size()));
    if (!getService(BackendService.class).isAlive()) {
      SonarLintConsole.get(project).info("Analysis skipped as SonarLint is not alive");
      return new AnalysisResult(LiveFindings.none(), files, trigger, Instant.now());
    }
    if (!getService(project, AnalysisReadinessCache.class).isReady()) {
      SonarLintConsole.get(project).info("Analysis skipped as the engine is not ready yet");
      return new AnalysisResult(LiveFindings.none(), files, trigger, Instant.now());
    }

    AnalysisScope scope;
    try {
      scope = AnalysisScope.defineFrom(project, files, trigger);
    } catch (InvalidBindingException e) {
      // nothing to do, SonarLintEngineManager already showed notification
      return new AnalysisResult(LiveFindings.none(), files, trigger, Instant.now());
    }

    // refresh should ideally not be done here, see SLCORE-729
    getService(project, NewCodePeriodCache.class).refreshAsync();
    var findingsCache = getService(project, FindingsCache.class);

    try {
      checkCanceled(indicator);

      var previousFindings = findingsCache.getSnapshot(files);

      if (scope.isEmpty()) {
        var analysisResult = new AnalysisResult(LiveFindings.none(), files, trigger, Instant.now());
        callback.onSuccess(analysisResult);
        return analysisResult;
      }

      System.out.println("came before analyzePerModule");
      var summary = analyzePerModule(scope, indicator, previousFindings);

      getService(SonarLintTelemetry.class).addReportedRules(summary.getReportedRuleKeys());

      indicator.setIndeterminate(false);
      indicator.setFraction(.9);

      summary.logInConsole();

      findingsCache.replaceFindings(summary.findings);

      checkCanceled(indicator);
      matchWithServerIssuesIfNeeded(summary.filesHavingIssuesByModule);
      checkCanceled(indicator);
      matchWithServerSecurityHotspotsIfNeeded(summary.filesHavingSecurityHotspotsByModule);

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

  private void matchWithServerIssuesIfNeeded(Map<Module, Collection<VirtualFile>> filesHavingIssuesByModule) {
    if (!filesHavingIssuesByModule.isEmpty()) {
      var backendService = getService(BackendService.class);
      filesHavingIssuesByModule.forEach((module, filesHavingIssues) -> backendService.trackWithServerIssues(module,
        filesHavingIssues.stream().collect(Collectors.toMap(Function.identity(),
          file -> getService(module.getProject(), FindingsCache.class).getIssuesForFile(file))), trigger.isShouldUpdateServerIssues()));
    }
  }

  private void matchWithServerSecurityHotspotsIfNeeded(Map<Module, Collection<VirtualFile>> filesHavingSecurityHotspotsByModule) {
    if (!filesHavingSecurityHotspotsByModule.isEmpty()) {
      var backendService = getService(BackendService.class);
      filesHavingSecurityHotspotsByModule.forEach((module, filesHavingHotspots) -> backendService.trackWithServerHotspots(module,
        filesHavingHotspots.stream().collect(Collectors.toMap(Function.identity(),
          file -> getService(module.getProject(), FindingsCache.class).getSecurityHotspotsForFile(file))), trigger.isShouldUpdateServerIssues()));
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
        runOnUiThreadAndWait(project, () -> Messages.showErrorDialog(dialogMsg, "Error Running SonarLint Analysis"));
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

  private Summary analyzePerModule(AnalysisScope scope, ProgressIndicator indicator, CachedFindings cachedFindings) {
    indicator.setIndeterminate(true);
    indicator.setText("Running SonarLint Analysis for " + scope.getDescription());

    var analyzer = getService(project, SonarLintAnalyzer.class);
    var progressMonitor = new TaskProgressMonitor(indicator, project, () -> cancelled);
    var results = new LinkedHashMap<Module, ModuleAnalysisResult>();
    RawFindingHandler rawFindingHandler;
    System.out.println("Came before finding streamer");
    try (var findingStreamer = new FindingStreamer(callback)) {
      rawFindingHandler = new RawFindingHandler(findingStreamer, cachedFindings);
      for (var entry : scope.getFilesByModule().entrySet()) {
        var module = entry.getKey();
        rawFindingHandler.setCurrentModule(module);
        results.put(module, analyzer.analyzeModule(module, entry.getValue(), rawFindingHandler, progressMonitor));
        checkCanceled(indicator);
      }
    }
    return summarize(scope, rawFindingHandler, results);
  }

  private Summary summarize(AnalysisScope scope, RawFindingHandler rawFindingHandler, Map<Module, ModuleAnalysisResult> resultsByModule) {
    var allFailedFiles = resultsByModule.values().stream().flatMap(r -> r.failedFiles().stream()).collect(toSet());
    var analyzedFiles = scope.getAllFilesToAnalyze();
    var issuesPerAnalyzedFile = getFindingsPerAnalyzedFile(rawFindingHandler.getIssuesPerFile(), analyzedFiles);
    var securityHotspotsPerAnalyzedFile = getFindingsPerAnalyzedFile(rawFindingHandler.getSecurityHotspotsPerFile(), analyzedFiles);
    var findings = new LiveFindings(issuesPerAnalyzedFile, securityHotspotsPerAnalyzedFile);
    return new Summary(project, scope.getFilesByModule(), allFailedFiles, rawFindingHandler.getRawIssueCount(), findings);
  }

  private static <T> Map<VirtualFile, Collection<T>> getFindingsPerAnalyzedFile(Map<VirtualFile, Collection<T>> detectedFindingsPerFile,
    Set<VirtualFile> analyzedFiles) {
    Map<VirtualFile, Collection<T>> findingsPerAnalyzedFile = analyzedFiles.stream().collect(toMap(Function.identity(),
      k -> new ArrayList<>()));
    findingsPerAnalyzedFile.putAll(detectedFindingsPerFile);
    return findingsPerAnalyzedFile;
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
      this.issuesCount = findings.getIssuesPerFile().values().stream().mapToLong(Collection::size).sum();
      this.onlyFailedFiles = failedFiles.containsAll(filesByModule.values().stream().flatMap(Collection::stream).collect(toSet()));
    }

    private static <L extends LiveFinding> Map<Module, Collection<VirtualFile>> filterFilesHavingFindingsByModule(Map<Module,
      Collection<VirtualFile>> filesByModule,
      Map<VirtualFile, Collection<L>> issuesPerFile) {
      var filesWithIssuesPerModule = new LinkedHashMap<Module, Collection<VirtualFile>>();

      for (var entry : filesByModule.entrySet()) {
        var moduleFilesWithIssues =
          entry.getValue().stream().filter(f -> !issuesPerFile.getOrDefault(f, Collections.emptyList()).isEmpty()).toList();
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
