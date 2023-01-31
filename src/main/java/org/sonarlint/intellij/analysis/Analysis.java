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
import com.intellij.openapi.application.ReadAction;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.config.Settings;
import org.sonarlint.intellij.core.ServerIssueUpdater;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.IssueMatcher;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.issue.LiveIssueBuilder;
import org.sonarlint.intellij.issue.tracking.Trackable;
import org.sonarlint.intellij.messages.AnalysisListener;
import org.sonarlint.intellij.notifications.SecretsNotifications;
import org.sonarlint.intellij.telemetry.SonarLintTelemetry;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.util.TaskProgressMonitor;
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.progress.CanceledException;

import static java.util.stream.Collectors.toList;
import static org.sonarlint.intellij.common.util.SonarLintUtils.pluralize;

public class Analysis implements Cancelable {
  private final Project project;
  private final Collection<VirtualFile> files;
  private final TriggerType trigger;
  private final boolean waitForServerIssues;
  private final AnalysisCallback callback;
  private boolean finished = false;
  private boolean cancelled;

  public Analysis(Project project, Collection<VirtualFile> files, TriggerType trigger, boolean waitForServerIssues,
    AnalysisCallback callback) {
    this.project = project;
    this.files = files;
    this.trigger = trigger;
    this.waitForServerIssues = waitForServerIssues;
    this.callback = callback;
  }

  public Collection<VirtualFile> getFiles() {
    return files;
  }

  public TriggerType getTrigger() {
    return trigger;
  }

  public boolean isForced() {
    return trigger == TriggerType.ACTION;
  }

  public AnalysisResult run(ProgressIndicator indicator) {
    try {
      notifyStart();
      return doRun(indicator);
    } finally {
      finished = true;
      if (!project.isDisposed()) {
        SonarLintUtils.getService(project, AnalysisStatus.class).stopRun();
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
    var console = SonarLintUtils.getService(project, SonarLintConsole.class);
    console.debug("Trigger: " + trigger);
    console.debug(String.format("[%s] %d file(s) submitted", trigger, files.size()));

    var liveIssueBuilder = SonarLintUtils.getService(project, LiveIssueBuilder.class);
    var manager = SonarLintUtils.getService(project, IssueManager.class);

    var issuesPerFile = new ConcurrentHashMap<VirtualFile, Collection<LiveIssue>>();

    var filesToClearIssues = new ArrayList<VirtualFile>();
    Map<Module, Collection<VirtualFile>> filesByModule;
    try {
      filesByModule = filterAndGetByModule(files, isForced(), filesToClearIssues);
    } catch (InvalidBindingException e) {
      // nothing to do, SonarLintEngineManager already showed notification
      return new AnalysisResult(Collections.emptyMap(), files, trigger, Instant.now());
    }
    var allFilesToAnalyze = filesByModule.entrySet().stream().flatMap(e -> e.getValue().stream()).collect(toList());

    // Cache everything that rely on issue store before clearing issues
    var firstAnalyzedFiles = cacheFirstAnalyzedFiles(manager, allFilesToAnalyze);
    var previousIssuesPerFile = collectPreviousIssuesPerFile(manager, allFilesToAnalyze);

    var rawIssueCounter = new AtomicInteger();

    try {
      checkCanceled(indicator);

      ReadAction.run(() -> {
        var filesToClear = new HashSet<>(filesToClearIssues);
        filesToClear.addAll(allFilesToAnalyze);
        manager.clearAllIssuesForFiles(filesToClear);
      });

      if (allFilesToAnalyze.isEmpty()) {
        var analysisResult = new AnalysisResult(Collections.emptyMap(), files, trigger, Instant.now());
        callback.onSuccess(analysisResult);
        return analysisResult;
      }

      var reportedRules = new HashSet<String>();
      var results = analyzePerModule(project, indicator, filesByModule,
        rawIssue -> processRawIssue(liveIssueBuilder, manager, firstAnalyzedFiles, issuesPerFile,
          previousIssuesPerFile, rawIssueCounter, rawIssue, reportedRules));

      var telemetry = SonarLintUtils.getService(SonarLintTelemetry.class);
      telemetry.addReportedRules(reportedRules);

      indicator.setIndeterminate(false);
      indicator.setFraction(.9);

      var allFailedAnalysisFiles = results.stream()
        .flatMap(r -> r.failedAnalysisFiles().stream())
        .collect(toList());

      var failedVirtualFiles = asVirtualFiles(allFailedAnalysisFiles);
      if (!failedVirtualFiles.containsAll(allFilesToAnalyze)) {
        logFoundIssuesIfAny(rawIssueCounter.get(), issuesPerFile);
      }

      finalizeIssueStore(manager, previousIssuesPerFile, allFilesToAnalyze, failedVirtualFiles);

      matchWithServerIssuesIfNeeded(indicator, filesByModule, issuesPerFile);

      var result = new AnalysisResult(issuesPerFile, files, trigger, Instant.now());
      callback.onSuccess(result);
      return result;
    } catch (CanceledException | ProcessCanceledException e1) {
      console.info("Analysis canceled");
    } catch (Throwable e) {
      handleError(e, indicator);
    }
    return new AnalysisResult(Collections.emptyMap(), files, trigger, Instant.now());
  }

  private Map<Module, Collection<VirtualFile>> filterAndGetByModule(Collection<VirtualFile> files, boolean forcedAnalysis,
    List<VirtualFile> filesToClearIssues)
    throws InvalidBindingException {
    var localFileExclusions = SonarLintUtils.getService(project, LocalFileExclusions.class);
    return localFileExclusions.retainNonExcludedFilesByModules(files, forcedAnalysis,
      (f, r) -> logExclusionAndAddToClearList(filesToClearIssues, f, r.excludeReason()));
  }

  private void logExclusionAndAddToClearList(List<VirtualFile> filesToClearIssues, VirtualFile file, String s) {
    logExclusion(file, s);
    filesToClearIssues.add(file);
  }

  private void logExclusion(VirtualFile f, String reason) {
    var console = SonarLintUtils.getService(project, SonarLintConsole.class);
    console.debug("File '" + f.getName() + "' excluded: " + reason);
  }

  private void matchWithServerIssuesIfNeeded(ProgressIndicator indicator, Map<Module, Collection<VirtualFile>> filesByModule,
    Map<VirtualFile, Collection<LiveIssue>> issuesPerFile) {
    var filesWithIssuesPerModule = new LinkedHashMap<Module, Collection<VirtualFile>>();

    for (var entry : filesByModule.entrySet()) {
      var moduleFilesWithIssues = entry.getValue().stream()
        .filter(f -> !issuesPerFile.getOrDefault(f, Collections.emptyList()).isEmpty())
        .collect(toList());
      if (!moduleFilesWithIssues.isEmpty()) {
        filesWithIssuesPerModule.put(entry.getKey(), moduleFilesWithIssues);
      }
    }
    if (!filesWithIssuesPerModule.isEmpty()) {
      var serverIssueUpdater = SonarLintUtils.getService(project, ServerIssueUpdater.class);
      if (shouldUpdateServerIssues(trigger)) {
        serverIssueUpdater.fetchAndMatchServerIssues(filesWithIssuesPerModule, indicator, waitForServerIssues);
      } else {
        serverIssueUpdater.matchServerIssues(filesWithIssuesPerModule);
      }
    }
  }

  private void finalizeIssueStore(IssueManager manager, Map<VirtualFile, Collection<Trackable>> previousIssuesPerFile,
    List<VirtualFile> allFilesToAnalyze,
    Set<VirtualFile> failedVirtualFiles) {
    allFilesToAnalyze.forEach(vFile -> {
      if (failedVirtualFiles.contains(vFile)) {
        SonarLintConsole.get(project).debug("Analysis of file '" + vFile.getPath() + "' might not be accurate because there were errors" +
          " during analysis");
      } else {
        // Remove previous issues that are unmatched, they are probably resolved
        var nonMatchedPreviousIssues = previousIssuesPerFile.get(vFile)
          .stream()
          .filter(LiveIssue.class::isInstance)
          .map(LiveIssue.class::cast)
          .collect(toList());
        // Even if there are no previous issues, call the method to mark the file as analyzed
        manager.removeResolvedIssues(vFile, nonMatchedPreviousIssues);
      }
    });
  }

  @NotNull
  private static Map<VirtualFile, Collection<Trackable>> collectPreviousIssuesPerFile(IssueManager manager,
    List<VirtualFile> allFilesToAnalyze) {
    var previousIssuesPerFile = new HashMap<VirtualFile, Collection<Trackable>>();
    allFilesToAnalyze.forEach(vFile -> previousIssuesPerFile.computeIfAbsent(vFile, f -> new ArrayList<>(manager.getPreviousIssues(f))));
    return previousIssuesPerFile;
  }

  private static Map<VirtualFile, Boolean> cacheFirstAnalyzedFiles(IssueManager manager, List<VirtualFile> allFilesToAnalyze) {
    var firstAnalyzedFiles = new HashMap<VirtualFile, Boolean>();
    allFilesToAnalyze.forEach(vFile -> firstAnalyzedFiles.computeIfAbsent(vFile, f -> !manager.wasAnalyzed(f)));
    return firstAnalyzedFiles;
  }

  private void processRawIssue(LiveIssueBuilder issueBuilder, IssueManager manager, Map<VirtualFile, Boolean> firstAnalyzedFiles,
    Map<VirtualFile, Collection<LiveIssue>> issuesPerFile, Map<VirtualFile, Collection<Trackable>> previousIssuesPerFile,
    AtomicInteger rawIssueCounter, Issue rawIssue, Set<String> reportedRules) {
    rawIssueCounter.incrementAndGet();

    // Do issue tracking for the single issue
    var inputFile = rawIssue.getInputFile();
    if (inputFile == null || inputFile.getPath() == null) {
      // ignore project level issues
      return;
    }
    VirtualFile vFile = inputFile.getClientObject();
    if (!vFile.isValid()) {
      // file is no longer valid (might have been deleted meanwhile) or there has been an error matching an issue in it
      return;
    }
    LiveIssue liveIssue;
    try {
      liveIssue = issueBuilder.buildLiveIssue(rawIssue, inputFile);
    } catch (IssueMatcher.NoMatchException e) {
      // File content is likely to have changed during the analysis, should be fixed in next analysis
      SonarLintConsole.get(project).debug("Failed to find location of issue for file: '" + vFile.getName() + "'." + e.getMessage());
      return;
    } catch (ProcessCanceledException e) {
      throw e;
    } catch (Exception e) {
      SonarLintConsole.get(project).error("Error finding location for issue", e);
      return;
    }

    if (firstAnalyzedFiles.get(vFile).booleanValue()) {
      // don't set creation date, as we don't know when the issue was actually created (SLI-86)
      issuesPerFile.computeIfAbsent(vFile, f -> new ArrayList<>()).add(liveIssue);
      manager.insertNewIssue(vFile, liveIssue);
    } else {
      var previousIssues = previousIssuesPerFile.get(vFile);
      var locallyTrackedIssue = manager.trackSingleIssue(vFile, previousIssues, liveIssue);
      issuesPerFile.computeIfAbsent(vFile, f -> new ArrayList<>()).add(locallyTrackedIssue);
    }

    reportedRules.add(liveIssue.getRuleKey());
    var sonarLintGlobalSettings = Settings.getGlobalSettings();
    if (sonarLintGlobalSettings.isSecretsNeverBeenAnalysed() && liveIssue.getRuleKey().contains(Language.SECRETS.getLanguageKey())) {
      SecretsNotifications.sendNotification(project);
      sonarLintGlobalSettings.rememberNotificationOnSecretsBeenSent();
    }
  }

  private void logFoundIssuesIfAny(int rawIssueCount, Map<VirtualFile, Collection<LiveIssue>> transformedIssues) {
    var issueStr = pluralize("issue", rawIssueCount);
    var console = SonarLintConsole.get(project);
    console.debug(String.format("Processed %d %s", rawIssueCount, issueStr));

    long issuesToShow = transformedIssues.values().stream()
      .mapToLong(Collection::size)
      .sum();

    var end = pluralize("issue", issuesToShow);
    console.info("Found " + issuesToShow + " " + end);
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

  private static Set<VirtualFile> asVirtualFiles(Collection<ClientInputFile> failedAnalysisFiles) {
    return failedAnalysisFiles.stream().map(f -> (VirtualFile) f.getClientObject()).collect(Collectors.toSet());
  }

  private void handleError(Throwable e, ProgressIndicator indicator) {
    // if cancelled, ignore any errors since they were most likely caused by the interrupt
    if (!isCancelled(indicator)) {
      var message = "Error running SonarLint analysis";
      var console = SonarLintConsole.get(project);
      console.error(message, e);

      if (indicator.isShowing()) {
        var dialogMsg = "SonarLint analysis failed: " + e.getMessage();
        ApplicationManager.getApplication().invokeAndWait(
          () -> Messages.showErrorDialog(dialogMsg, "Error Running SonarLint Analysis"), ModalityState.defaultModalityState());
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

  private List<AnalysisResults> analyzePerModule(Project project, ProgressIndicator indicator,
    Map<Module, Collection<VirtualFile>> filesByModule, IssueListener listener) {
    SonarLintAnalyzer analyzer = SonarLintUtils.getService(project, SonarLintAnalyzer.class);

    indicator.setIndeterminate(true);
    int numModules = filesByModule.keySet().size();
    var suffix = "";
    if (numModules > 1) {
      suffix = String.format(" in %d modules", numModules);
    }

    var allFilesToAnalyze = filesByModule.entrySet().stream().flatMap(e -> e.getValue().stream()).collect(toList());
    long numFiles = allFilesToAnalyze.size();
    if (numFiles > 1) {
      indicator.setText("Running SonarLint Analysis for " + numFiles + " files" + suffix);
    } else {
      indicator.setText("Running SonarLint Analysis for '" + getFileName(allFilesToAnalyze.get(0)) + "'");
    }

    var progressMonitor = new TaskProgressMonitor(indicator, project, () -> cancelled);
    var results = new LinkedList<AnalysisResults>();

    for (var entry : filesByModule.entrySet()) {
      results.add(analyzer.analyzeModule(entry.getKey(), entry.getValue(), listener, progressMonitor));
      checkCanceled(indicator);
    }
    return results;
  }

  private static String getFileName(VirtualFile file) {
    return file.getName();
  }
}
