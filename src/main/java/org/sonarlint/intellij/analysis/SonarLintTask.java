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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.core.ServerIssueUpdater;
import org.sonarlint.intellij.editor.IssueStreamingIssueListener;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.IssueProcessor;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.messages.IssueStoreListener;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarlint.intellij.util.TaskProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.exceptions.CanceledException;

import static com.intellij.openapi.application.ActionsKt.runInEdt;
import static java.util.stream.Collectors.toList;

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
    IssueProcessor processor = SonarLintUtils.getService(myProject, IssueProcessor.class);

    Collection<Issue> issues = new ArrayList<>();
    IssueStreamingIssueListener issueListener = new IssueStreamingIssueListener((issue -> {
      issues.add(issue);
      indicator.setText("Updating SonarLint issue: " + issue.getRuleKey());
      checkCanceled(indicator, myProject);
      runInEdt(ModalityState.any(), () -> {
        processor.process(issue);
        myProject.getMessageBus().syncPublisher(IssueStoreListener.SONARLINT_ISSUE_STORE_TOPIC).allChanged();
        return null;
      });
      return null;
    }));

     List<ClientInputFile> allFailedAnalysisFiles;
     if (getJob().allFiles().findAny().isPresent()) {

      List<AnalysisResults> results = analyze(myProject, indicator, issueListener);

       // last chance to cancel (to avoid the possibility of having interrupt flag set)
       checkCanceled(indicator, myProject);

       LOGGER.info("SonarLint analysis done");

       indicator.setIndeterminate(false);
       indicator.setFraction(.9);

       allFailedAnalysisFiles = results.stream()
         .flatMap(r -> r.failedAnalysisFiles().stream())
         .collect(Collectors.toList());
     } else {
       allFailedAnalysisFiles = Collections.emptyList();
     }

    Map<VirtualFile, Collection<LiveIssue>> transformedIssues = processor.transformIssues(issues, job.allFiles().collect(Collectors.toList()), allFailedAnalysisFiles);
    if (shouldUpdateServerIssues(job.trigger())) {
      Map<Module, Collection<VirtualFile>> filesWithIssuesPerModule = new LinkedHashMap<>();

      for (Map.Entry<Module, Collection<VirtualFile>> e : job.filesPerModule().entrySet()) {
        Collection<VirtualFile> moduleFilesWithIssues = e.getValue().stream()
          .filter(f -> !transformedIssues.getOrDefault(f, Collections.emptyList()).isEmpty())
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

  public static boolean shouldUpdateServerIssues(TriggerType trigger) {
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

  private List<AnalysisResults> analyze(Project project, ProgressIndicator indicator, IssueListener listener) {
    IssueManager manager = SonarLintUtils.getService(myProject, IssueManager.class);
    manager.analysisStarted();

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
    manager.analysisFinished();
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
