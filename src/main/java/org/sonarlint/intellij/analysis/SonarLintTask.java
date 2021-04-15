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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
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
import org.sonarlint.intellij.core.ServerIssueUpdater;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.IssueMatcher;
import org.sonarlint.intellij.issue.LiveIssueBuilder;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.issue.tracking.Trackable;
import org.sonarlint.intellij.issue.vulnerabilities.TaintVulnerabilitiesPresenter;
import org.sonarlint.intellij.messages.TaskListener;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarlint.intellij.util.TaskProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.exceptions.CanceledException;

import static java.util.stream.Collectors.toList;
import static org.sonarlint.intellij.util.SonarLintUtils.pluralize;

public class SonarLintTask extends Task.Backgroundable {
  private static final Logger LOGGER = Logger.getInstance(SonarLintTask.class);
  protected final SonarLintJob job;
  protected final boolean modal;
  private final boolean startInBackground;
  protected final Project myProject;
  private boolean finished = false;
  private boolean cancelled;

  public SonarLintTask(Project project, SonarLintJob job, boolean background) {
    this(project, job, false, background);
  }

  /**
   * An IntelliJ task, that will run with its Progress Manager.
   *
   * @param modal      If true and background is false, it will be a blocking task, without the possibility to send it to background.
   * @param background Whether it should start in the foreground or background.
   */
  public SonarLintTask(Project project, SonarLintJob job, boolean modal, boolean background) {
    super(job.project(), "SonarLint Analysis", true);
    myProject = project;
    this.job = job;
    this.modal = modal;
    this.startInBackground = background;
  }

  @Override
  public boolean shouldStartInBackground() {
    return startInBackground;
  }

  @Override
  public boolean isConditionalModal() {
    return modal;
  }

  private static String getFileName(VirtualFile file) {
    return file.getName();
  }

  public SonarLintJob getJob() {
    return job;
  }

  @Override
  public void run(ProgressIndicator indicator) {
    LiveIssueBuilder liveIssueBuilder = SonarLintUtils.getService(myProject, LiveIssueBuilder.class);
    IssueManager manager = SonarLintUtils.getService(myProject, IssueManager.class);

    Map<VirtualFile, Collection<LiveIssue>> issuesPerFile = new ConcurrentHashMap<>();

    List<VirtualFile> allFilesToAnalyze = job.allFiles().collect(toList());
    // Cache everything that rely on issue store before clearing issues
    Map<VirtualFile, Boolean> firstAnalyzedFiles = cacheFirstAnalyzedFiles(manager, allFilesToAnalyze);
    Map<VirtualFile, Collection<Trackable>> previousIssuesPerFile = collectPreviousIssuesPerFile(manager, allFilesToAnalyze);

    AtomicInteger rawIssueCounter = new AtomicInteger();

    try {
      checkCanceled(indicator, myProject);

      ReadAction.run(() -> {
        Set<VirtualFile> filesToClear = new HashSet<>(job.filesToClearIssues());
        filesToClear.addAll(allFilesToAnalyze);
        manager.clearAllIssuesForFiles(filesToClear);
      });

      if (allFilesToAnalyze.isEmpty()) {
        job.callback().onSuccess(Collections.emptySet());
        return;
      }

      List<AnalysisResults> results = analyzePerModule(myProject, indicator,
        rawIssue -> processRawIssue(liveIssueBuilder, manager, firstAnalyzedFiles, issuesPerFile, previousIssuesPerFile, rawIssueCounter, rawIssue));

      LOGGER.info("SonarLint analysis done");

      indicator.setIndeterminate(false);
      indicator.setFraction(.9);

      List<ClientInputFile> allFailedAnalysisFiles = results.stream()
        .flatMap(r -> r.failedAnalysisFiles().stream())
        .collect(toList());

      Set<VirtualFile> failedVirtualFiles = asVirtualFiles(allFailedAnalysisFiles);
      if (!failedVirtualFiles.containsAll(allFilesToAnalyze)) {
        logFoundIssuesIfAny(rawIssueCounter.get(), issuesPerFile);
      }

      finalizeIssueStore(manager, previousIssuesPerFile, allFilesToAnalyze, failedVirtualFiles);

      matchWithServerIssuesIfNeeded(indicator, issuesPerFile);

      if (SonarLintUtils.enableTaintVulnerabilities()) {
        SonarLintUtils.getService(myProject, TaintVulnerabilitiesPresenter.class).presentTaintVulnerabilitiesForOpenFiles();
      }

      job.callback().onSuccess(failedVirtualFiles);
    } catch (CanceledException e1) {
      SonarLintConsole console = SonarLintConsole.get(job.project());
      console.info("Analysis canceled");
    } catch (Throwable e) {
      handleError(e, indicator);
    } finally {
      if (!myProject.isDisposed()) {
        myProject.getMessageBus().syncPublisher(TaskListener.SONARLINT_TASK_TOPIC).ended(job);
      }
    }
  }

  private void matchWithServerIssuesIfNeeded(ProgressIndicator indicator, Map<VirtualFile, Collection<LiveIssue>> issuesPerFile) {
    if (shouldUpdateServerIssues(job.trigger())) {
      Map<Module, Collection<VirtualFile>> filesWithIssuesPerModule = new LinkedHashMap<>();

      for (Map.Entry<Module, Collection<VirtualFile>> e : job.filesPerModule().entrySet()) {
        Collection<VirtualFile> moduleFilesWithIssues = e.getValue().stream()
          .filter(f -> !issuesPerFile.getOrDefault(f, Collections.emptyList()).isEmpty())
          .collect(toList());
        if (!moduleFilesWithIssues.isEmpty()) {
          filesWithIssuesPerModule.put(e.getKey(), moduleFilesWithIssues);
        }
      }

      if (!filesWithIssuesPerModule.isEmpty()) {
        ServerIssueUpdater serverIssueUpdater = SonarLintUtils.getService(myProject, ServerIssueUpdater.class);
        serverIssueUpdater.fetchAndMatchServerIssues(filesWithIssuesPerModule, indicator, job.waitForServerIssues());
      }
    }
  }

  private void finalizeIssueStore(IssueManager manager, Map<VirtualFile, Collection<Trackable>> previousIssuesPerFile, List<VirtualFile> allFilesToAnalyze,
    Set<VirtualFile> failedVirtualFiles) {
    allFilesToAnalyze.forEach(vFile -> {
      if (failedVirtualFiles.contains(vFile)) {
        SonarLintConsole.get(myProject).debug("Analysis of file '" + vFile.getPath() + "' might not be accurate because there were errors during analysis");
      } else {
        // Remove previous issues that are unmatched, they are probably resolved
        List<LiveIssue> nonMatchedPreviousIssues = previousIssuesPerFile.get(vFile)
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
  private static Map<VirtualFile, Collection<Trackable>> collectPreviousIssuesPerFile(IssueManager manager, List<VirtualFile> allFilesToAnalyze) {
    Map<VirtualFile, Collection<Trackable>> previousIssuesPerFile = new HashMap<>();
    allFilesToAnalyze.forEach(vFile -> previousIssuesPerFile.computeIfAbsent(vFile, f -> new ArrayList<>(manager.getPreviousIssues(f))));
    return previousIssuesPerFile;
  }

  private static Map<VirtualFile, Boolean> cacheFirstAnalyzedFiles(IssueManager manager, List<VirtualFile> allFilesToAnalyze) {
    Map<VirtualFile, Boolean> firstAnalyzedFiles = new HashMap<>();
    allFilesToAnalyze.forEach(vFile -> firstAnalyzedFiles.computeIfAbsent(vFile, f -> !manager.wasAnalyzed(f)));
    return firstAnalyzedFiles;
  }

  private void processRawIssue(LiveIssueBuilder issueBuilder, IssueManager manager, Map<VirtualFile, Boolean> firstAnalyzedFiles,
    Map<VirtualFile, Collection<LiveIssue>> issuesPerFile, Map<VirtualFile, Collection<Trackable>> previousIssuesPerFile, AtomicInteger rawIssueCounter,
    org.sonarsource.sonarlint.core.client.api.common.analysis.Issue rawIssue) {
    rawIssueCounter.incrementAndGet();

    // Do issue tracking for the single issue
    ClientInputFile inputFile = rawIssue.getInputFile();
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
      SonarLintConsole.get(myProject).debug("Failed to find location of issue for file: '" + vFile.getName() + "'." + e.getMessage());
      return;
    } catch (Exception e) {
      LOGGER.error("Error finding location for issue", e);
      return;
    }

    if (firstAnalyzedFiles.get(vFile).booleanValue()) {
      // don't set creation date, as we don't know when the issue was actually created (SLI-86)
      issuesPerFile.computeIfAbsent(vFile, f -> new ArrayList<>()).add(liveIssue);
      manager.insertNewIssue(vFile, liveIssue);
    } else {
      Collection<Trackable> previousIssues = previousIssuesPerFile.get(vFile);
      LiveIssue locallyTrackedIssue = manager.trackSingleIssue(vFile, previousIssues, liveIssue);
      issuesPerFile.computeIfAbsent(vFile, f -> new ArrayList<>()).add(locallyTrackedIssue);
    }
  }

  private void logFoundIssuesIfAny(int rawIssueCount, Map<VirtualFile, Collection<LiveIssue>> transformedIssues) {
    String issueStr = pluralize("issue", rawIssueCount);
    SonarLintConsole console = SonarLintConsole.get(myProject);
    console.debug(String.format("Processed %d %s", rawIssueCount, issueStr));

    long issuesToShow = transformedIssues.values().stream()
      .mapToLong(Collection::size)
      .sum();

    String end = pluralize("issue", issuesToShow);
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
    if (!indicator.isCanceled()) {
      String msg = "Error running SonarLint analysis";
      SonarLintConsole console = SonarLintConsole.get(job.project());
      console.error(msg, e);
      LOGGER.warn(msg, e);

      if (indicator.isShowing()) {
        String dialogMsg = "SonarLint analysis failed: " + e.getMessage();
        ApplicationManager.getApplication().invokeAndWait(
          () -> Messages.showErrorDialog(dialogMsg, "Error Running SonarLint Analysis"), ModalityState.defaultModalityState());
      }

      AnalysisCallback callback = job.callback();
      callback.onError(e);
    }
  }

  private void checkCanceled(ProgressIndicator indicator, Project project) {
    if (cancelled || indicator.isCanceled() || project.isDisposed() || Thread.currentThread().isInterrupted()) {
      throw new CanceledException();
    }
  }

  private List<AnalysisResults> analyzePerModule(Project project, ProgressIndicator indicator, IssueListener listener) {
    SonarLintAnalyzer analyzer = SonarLintUtils.getService(project, SonarLintAnalyzer.class);

    indicator.setIndeterminate(true);
    int numModules = job.filesPerModule().keySet().size();
    String suffix = "";
    if (numModules > 1) {
      suffix = String.format(" in %d modules", numModules);
    }

    long numFiles = job.allFiles().count();
    if (numFiles > 1) {
      indicator.setText("Running SonarLint Analysis for " + numFiles + " files" + suffix);
    } else {
      indicator.setText("Running SonarLint Analysis for '" + getFileName(job.allFiles().findFirst().get()) + "'");
    }

    LOGGER.info(indicator.getText());

    ProgressMonitor progressMonitor = new TaskProgressMonitor(indicator, myProject, () -> cancelled);
    List<AnalysisResults> results = new LinkedList<>();

    for (Map.Entry<Module, Collection<VirtualFile>> e : job.filesPerModule().entrySet()) {
      results.add(analyzer.analyzeModule(e.getKey(), e.getValue(), listener, progressMonitor));
      checkCanceled(indicator, myProject);
    }
    return results;
  }

  @Override
  public void onFinished() {
    this.finished = true;
  }

  public boolean isFinished() {
    return finished;
  }

  public void cancel() {
    this.cancelled = true;
  }
}
