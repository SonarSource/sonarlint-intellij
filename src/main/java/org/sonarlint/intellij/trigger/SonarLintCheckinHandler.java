/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.util.PairConsumer;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.sonarlint.intellij.actions.SonarLintToolWindow;
import org.sonarlint.intellij.analysis.AnalysisResult;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;
import org.sonarlint.intellij.analysis.RunningAnalysesTracker;
import org.sonarlint.intellij.cayc.CleanAsYouCodeService;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.finding.LiveFindings;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.telemetry.SonarLintTelemetry;
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AnalysisReportingType;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.config.Settings.getGlobalSettings;
import static org.sonarlint.intellij.tasks.TaskRunnerKt.runModalTaskWithResult;
import static org.sonarlint.intellij.ui.UiUtils.runOnUiThread;
import static org.sonarlint.intellij.util.ProgressUtils.waitForFuture;

public class SonarLintCheckinHandler extends CheckinHandler {
  private static final String ACTIVATED_OPTION_NAME = "SONARLINT_PRECOMMIT_ANALYSIS";
  private static final int PRE_COMMIT_TIMEOUT = 60_000;

  private final Project project;
  private final CheckinProjectPanel checkinPanel;
  private final ScheduledExecutorService scheduledExecutor;

  private JCheckBox checkBox;

  public SonarLintCheckinHandler(Project project, CheckinProjectPanel checkinPanel) {
    this.project = project;
    this.checkinPanel = checkinPanel;
    this.scheduledExecutor = newSingleThreadScheduledExecutor();
  }

  @Override
  @Nullable
  public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
    this.checkBox = new NonFocusableCheckBox("Perform SonarQube for IDE analysis");
    return new MyRefreshableOnComponent(checkBox);
  }

  @Override
  public ReturnResult beforeCheckin(@Nullable CommitExecutor executor, PairConsumer<Object, Object> additionalDataConsumer) {
    if (checkBox != null && !checkBox.isSelected()) {
      return ReturnResult.COMMIT;
    }

    getService(SonarLintTelemetry.class).analysisReportingTriggered(AnalysisReportingType.PRE_COMMIT_ANALYSIS_TYPE);
    var affectedFiles = new HashSet<>(checkinPanel.getVirtualFiles());

    try {
      var analysisIdsByCallback = getService(project, AnalysisSubmitter.class).analyzeFilesPreCommit(affectedFiles);

      if (analysisIdsByCallback == null) {
        SonarLintConsole.get(project).debug("Pre-commit analysis cancelled because analysis did not start");
        return ReturnResult.COMMIT;
      }

      var analysisIds = analysisIdsByCallback.getRight();

      var completed = runModalTaskWithResult(project, "Waiting for SonarQube for IDE Analysis", indicator -> {
        var future = new CompletableFuture<Boolean>();
        var startTime = System.currentTimeMillis();
        var checkTask = runPreCommitAnalysis(future, indicator, analysisIds, startTime);
        // Avoid busy-waiting
        scheduledExecutor.scheduleAtFixedRate(checkTask, 0, 100, TimeUnit.MILLISECONDS);
        return waitForFuture(indicator, future);
      });

      if (Boolean.FALSE.equals(completed) || !analysisIdsByCallback.getLeft().analysisSucceeded()) {
        SonarLintConsole.get(project).debug("Pre-commit analysis failed");
        return showFailureMessage("Error analysing " + affectedFiles.size() + " changed file(s).");
      }

      var results = analysisIdsByCallback.getLeft().getResults();
      return processResults(results);
    } catch (ProcessCanceledException e) {
      SonarLintConsole.get(project).debug("Pre-commit analysis cancelled by user");
      throw e;
    } catch (Exception e) {
      return handleError(e, affectedFiles.size());
    }
  }

  private Runnable runPreCommitAnalysis(CompletableFuture<Boolean> future, ProgressIndicator indicator, List<UUID> analysisIds, long startTime) {
    return () -> {
      if (future.isDone()) return;
      if (indicator.isCanceled()) {
        future.cancel(true);
        return;
      }
      var elapsed = System.currentTimeMillis() - startTime;
      if (elapsed > PRE_COMMIT_TIMEOUT) {
        future.complete(false);
        return;
      }

      boolean anyRunning = analysisIds.stream()
        .map(id -> getService(project, RunningAnalysesTracker.class).getById(id))
        .anyMatch(Objects::nonNull);

      if (!anyRunning) {
        future.complete(true);
      }
    };
  }

  private ReturnResult handleError(Exception e, int numFiles) {
    var msg = "Error analysing " + numFiles + " changed file(s).";
    if (e.getMessage() != null) {
      msg = msg + ": " + e.getMessage();
    }
    SonarLintConsole.get(project).error(msg, e);
    return showFailureMessage(msg);
  }

  private ReturnResult processResults(List<AnalysisResult> results) {
    var issuesPerFile = results.stream()
      .flatMap(result -> result.getFindings().getIssuesPerFile().entrySet().stream())
      .collect(Collectors.toMap(
        Map.Entry::getKey,
        Map.Entry::getValue,
        (issues1, issues2) -> {
          List<LiveIssue> merged = new ArrayList<>();
          merged.addAll(issues1);
          merged.addAll(issues2);
          return merged;
        }));

    var shouldFocusOnNewCode = getService(CleanAsYouCodeService.class).shouldFocusOnNewCode();

    var numIssues = issuesPerFile.entrySet().stream()
      .flatMap(e -> e.getValue().stream())
      .filter(Predicate.not(LiveIssue::isResolved))
      .filter(issue -> !shouldFocusOnNewCode || issue.isOnNewCode())
      .count();

    var numBlockerIssues = issuesPerFile.entrySet().stream()
      .flatMap(e -> e.getValue().stream())
      .filter(Predicate.not(LiveIssue::isResolved))
      .filter(issue -> !shouldFocusOnNewCode || issue.isOnNewCode())
      .filter(i -> (i.getHighestImpact() != null && i.getHighestImpact().name().equals(ImpactSeverity.BLOCKER.name()))
        || (i.getUserSeverity() != null && i.getUserSeverity().name().equals(IssueSeverity.BLOCKER.name())))
      .count();

    if (numIssues == 0) {
      return ReturnResult.COMMIT;
    }

    var numFiles = issuesPerFile.size();

    var issues = issuesPerFile.values().stream().flatMap(Collection::stream).toList();
    var numSecretsIssues = issues.stream()
      .filter(issue -> !shouldFocusOnNewCode || issue.isOnNewCode())
      .filter(issue -> issue.getRuleKey().startsWith("secrets"))
      .count();
    var msg = createMessage(numFiles, numIssues, numBlockerIssues, numSecretsIssues);

    var choice = showYesNoCancel(msg);

    if (choice == ReturnResult.CLOSE_WINDOW) {
      var hotspotsPerFile = results.stream()
        .flatMap(result -> result.getFindings().getSecurityHotspotsPerFile().entrySet().stream())
        .collect(Collectors.toMap(
          Map.Entry::getKey,
          Map.Entry::getValue,
          (issues1, issues2) -> {
            List<LiveSecurityHotspot> merged = new ArrayList<>();
            merged.addAll(issues1);
            merged.addAll(issues2);
            return merged;
          }));
      var allFilesAnalyzed = results.stream().flatMap(result -> result.getAnalyzedFiles().stream()).toList();
      var result = new AnalysisResult(null, new LiveFindings(issuesPerFile, hotspotsPerFile), allFilesAnalyzed, results.get(0).getAnalysisDate());
      showChangedFilesTab(result);
    }
    return choice;
  }

  private static String createMessage(long filesAnalyzed, long numIssues, long numBlockerIssues, long numSecretsIssues) {
    var files = filesAnalyzed == 1 ? "file" : "files";
    var issues = numIssues == 1 ? "issue" : "issues";

    var warningAboutLeakedSecrets = "";
    if (numSecretsIssues > 0) {
      var secretWord = SonarLintUtils.pluralize("secret", numSecretsIssues);
      warningAboutLeakedSecrets = String.format("""
        

        SonarQube for IDE analysis found %d %s. Committed secrets may lead to unauthorized system access.""", numSecretsIssues, secretWord);
    }
    var message = new StringBuilder();
    if (numBlockerIssues > 0) {
      var blocker = SonarLintUtils.pluralize("issue", numBlockerIssues);
      message.append(String.format("SonarQube for IDE analysis on %d %s found %d %s (including %d blocker %s)", filesAnalyzed, files,
        numIssues, issues, numBlockerIssues, blocker));
    } else {
      message.append(String.format("SonarQube for IDE analysis on %d %s found %d %s", filesAnalyzed, files, numIssues, issues));
    }
    message.append(warningAboutLeakedSecrets);
    return message.toString();
  }

  private ReturnResult showYesNoCancel(String resultStr) {
    final var answer = Messages.showYesNoCancelDialog(project,
      resultStr,
      "SonarQube for IDE Pre-Commit Results",
      "&Review Issues",
      "C&ontinue",
      "Cancel",
      UIUtil.getWarningIcon());

    if (answer == Messages.YES) {
      return ReturnResult.CLOSE_WINDOW;
    } else if (answer == Messages.CANCEL) {
      return ReturnResult.CANCEL;
    } else {
      return ReturnResult.COMMIT;
    }
  }

  private ReturnResult showFailureMessage(String msg) {
    final var answer = Messages.showOkCancelDialog(project,
      msg,
      "SonarQube for IDE Pre-Commit Results",
      "C&ontinue",
      "Abort",
      UIUtil.getErrorIcon());

    if (answer == Messages.OK) {
      return ReturnResult.COMMIT;
    } else if (answer == Messages.CANCEL) {
      return ReturnResult.CANCEL;
    } else {
      return ReturnResult.COMMIT;
    }
  }

  private void showChangedFilesTab(AnalysisResult analysisResult) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      getService(project, SonarLintToolWindow.class).openReportTab(analysisResult);
    } else {
      runOnUiThread(project, () -> getService(project, SonarLintToolWindow.class).openReportTab(analysisResult));
    }
  }

  private class MyRefreshableOnComponent implements RefreshableOnComponent, UnnamedConfigurable {
    private final JCheckBox checkBox;

    private MyRefreshableOnComponent(JCheckBox checkBox) {
      this.checkBox = checkBox;
    }

    @Override
    public JComponent getComponent() {
      var panel = new JPanel(new BorderLayout());
      panel.add(checkBox);
      var dumb = DumbService.isDumb(project);
      checkBox.setEnabled(!dumb);
      checkBox.setToolTipText(dumb ? "SonarQube for IDE analysis is impossible until indices are up-to-date" : "");
      return panel;
    }

    @Override
    public void saveState() {
      PropertiesComponent.getInstance(project).setValue(ACTIVATED_OPTION_NAME, Boolean.toString(checkBox.isSelected()));
    }

    @Override
    public void restoreState() {
      checkBox.setSelected(getSavedStateOrDefault());
    }

    private boolean getSavedStateOrDefault() {
      var props = PropertiesComponent.getInstance(project);
      return props.getBoolean(ACTIVATED_OPTION_NAME, getGlobalSettings().isAutoTrigger());
    }

    @Override
    public @Nullable JComponent createComponent() {
      return getComponent();
    }

    @Override
    public boolean isModified() {
      return checkBox.isSelected() != getSavedStateOrDefault();
    }

    @Override
    public void apply() {
      saveState();
    }

    @Override
    public void reset() {
      restoreState();
    }
  }
}
