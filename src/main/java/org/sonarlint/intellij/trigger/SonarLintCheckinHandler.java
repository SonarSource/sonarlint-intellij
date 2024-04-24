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
package org.sonarlint.intellij.trigger;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.UnnamedConfigurable;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.function.Predicate;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.actions.SonarLintToolWindow;
import org.sonarlint.intellij.analysis.AnalysisResult;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;
import org.sonarlint.intellij.cayc.CleanAsYouCodeService;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.config.Settings.getGlobalSettings;

public class SonarLintCheckinHandler extends CheckinHandler {
  private static final Logger LOGGER = Logger.getInstance(SonarLintCheckinHandler.class);
  private static final String ACTIVATED_OPTION_NAME = "SONARLINT_PRECOMMIT_ANALYSIS";

  private final Project project;
  private final CheckinProjectPanel checkinPanel;
  private JCheckBox checkBox;

  public SonarLintCheckinHandler(Project project, CheckinProjectPanel checkinPanel) {
    this.project = project;
    this.checkinPanel = checkinPanel;
  }

  @Override
  @Nullable
  public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
    this.checkBox = new NonFocusableCheckBox("Perform SonarLint analysis");
    return new MyRefreshableOnComponent(checkBox);
  }

  @Override
  public ReturnResult beforeCheckin(@Nullable CommitExecutor executor, PairConsumer<Object, Object> additionalDataConsumer) {
    if (checkBox != null && !checkBox.isSelected()) {
      return ReturnResult.COMMIT;
    }

    // de-duplicate as the same file can be present several times in the panel (e.g. in several changelists)
    var affectedFiles = new HashSet<>(checkinPanel.getVirtualFiles());
    // this will block EDT (modal)
    try {
      var result = SonarLintUtils.getService(project, AnalysisSubmitter.class).analyzeFilesPreCommit(affectedFiles);
      if (result == null) {
        return ReturnResult.CANCEL;
      }
      return processResult(result);
    } catch (Exception e) {
      handleError(e, affectedFiles.size());
      return ReturnResult.CANCEL;
    }
  }

  private void handleError(Exception e, int numFiles) {
    var msg = "SonarLint - Error analysing " + numFiles + " changed file(s).";
    if (e.getMessage() != null) {
      msg = msg + ": " + e.getMessage();
    }
    LOGGER.info(msg, e);
    Messages.showErrorDialog(project, msg, "Error Analysing Files");
  }

  private ReturnResult processResult(AnalysisResult result) {
    var issuesPerFile = result.getFindings().getIssuesPerFile();
    var shouldFocusOnNewCode = getService(CleanAsYouCodeService.class).shouldFocusOnNewCode(project);

    var numIssues = issuesPerFile.entrySet().stream()
      .flatMap(e -> e.getValue().stream())
      .filter(Predicate.not(LiveIssue::isResolved))
      .filter(issue -> !shouldFocusOnNewCode || issue.isOnNewCode())
      .count();

    var numBlockerIssues = issuesPerFile.entrySet().stream()
      .flatMap(e -> e.getValue().stream())
      .filter(Predicate.not(LiveIssue::isResolved))
      .filter(issue -> !shouldFocusOnNewCode || issue.isOnNewCode())
      .filter(i -> i.getUserSeverity() != null && i.getUserSeverity().name().equals(IssueSeverity.BLOCKER.name()))
      .count();

    if (numIssues == 0) {
      return ReturnResult.COMMIT;
    }

    var numFiles = issuesPerFile.keySet().size();

    var issues = issuesPerFile.values().stream().flatMap(Collection::stream).toList();
    var numSecretsIssues = issues.stream()
      .filter(issue -> !shouldFocusOnNewCode || issue.isOnNewCode())
      .filter(issue -> issue.getRuleKey()
        .startsWith(SonarLanguage.SECRETS.getSonarLanguageKey()))
      .count();
    var msg = createMessage(numFiles, numIssues, numBlockerIssues, numSecretsIssues);

    var choice = showYesNoCancel(msg);

    if (choice == ReturnResult.CLOSE_WINDOW) {
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
        

        SonarLint analysis found %d %s. Committed secrets may lead to unauthorized system access.""", numSecretsIssues, secretWord);
    }
    var message = new StringBuilder();
    if (numBlockerIssues > 0) {
      var blocker = SonarLintUtils.pluralize("issue", numBlockerIssues);
      message.append(String.format("SonarLint analysis on %d %s found %d %s (including %d blocker %s)", filesAnalyzed, files,
        numIssues, issues, numBlockerIssues, blocker));
    } else {
      message.append(String.format("SonarLint analysis on %d %s found %d %s", filesAnalyzed, files, numIssues, issues));
    }
    message.append(warningAboutLeakedSecrets);
    return message.toString();
  }

  private ReturnResult showYesNoCancel(String resultStr) {
    final var answer = Messages.showYesNoCancelDialog(project,
      resultStr,
      "SonarLint Analysis Results",
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

  private void showChangedFilesTab(AnalysisResult analysisResult) {
    SonarLintUtils.getService(project, SonarLintToolWindow.class).openReportTab(analysisResult);
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
      checkBox.setToolTipText(dumb ? "SonarLint analysis is impossible until indices are up-to-date" : "");
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
