/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.NonFocusableCheckBox;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.PairConsumer;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.analysis.AnalysisResult;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.issue.ChangedFilesIssues;
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory;
import org.sonarlint.intellij.util.SonarLintUtils;

public class SonarLintCheckinHandler extends CheckinHandler {
  private static final Logger LOGGER = Logger.getInstance(SonarLintCheckinHandler.class);
  private static final String ACTIVATED_OPTION_NAME = "SONARLINT_PRECOMMIT_ANALYSIS";
  private static final String SONARLINT_TOOL_WINDOW_ID = "SonarLint";

  private final ToolWindowManager toolWindowManager;
  private final SonarLintGlobalSettings globalSettings;
  private final Collection<VirtualFile> affectedFiles;
  private final Project project;

  public SonarLintCheckinHandler(ToolWindowManager toolWindowManager, SonarLintGlobalSettings globalSettings, Collection<VirtualFile> affectedFiles,
    Project project) {
    this.toolWindowManager = toolWindowManager;
    this.globalSettings = globalSettings;
    this.affectedFiles = affectedFiles;
    this.project = project;
  }

  @Override
  @Nullable
  public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
    final JCheckBox checkBox = new NonFocusableCheckBox("Perform SonarLint Analysis");
    return new MyRefreshableOnComponent(checkBox);
  }

  @Override
  public ReturnResult beforeCheckin(@Nullable CommitExecutor executor, PairConsumer<Object, Object> additionalDataConsumer) {
    SonarLintSubmitter submitter = SonarLintUtils.get(project, SonarLintSubmitter.class);
    CompletableFuture<AnalysisResult> result = submitter.submitFiles(affectedFiles.toArray(new VirtualFile[affectedFiles.size()]), TriggerType.CHECK_IN, false, true);
    return processResult(result);
  }

  private ReturnResult processResult(Future<AnalysisResult> futureResult) {
    try {
      // this will block EDT! The analysis task can't use EDT or it will dead lock
      AnalysisResult result = futureResult.get();

      ChangedFilesIssues changedFilesIssues = SonarLintUtils.get(project, ChangedFilesIssues.class);
      changedFilesIssues.set(result.issues());

      if (result.numberIssues() == 0) {
        return ReturnResult.COMMIT;
      }

      String resultStr = String.format("SonarLint analysis on %d files found %d issues", result.filesAnalysed(), result.numberIssues());
      if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
        LOGGER.info(resultStr);
        return ReturnResult.CANCEL;
      }

      return showYesNoCancel(resultStr);
    } catch (Exception e) {
      String msg = "SonarLint - Error analysing " + affectedFiles.size() + " changed files.";
      if (e.getMessage() != null) {
        msg = msg + ": " + e.getMessage();
      }
      LOGGER.error("msg", e);
      Messages.showErrorDialog(project, msg, "Error Analysing Files");
      return ReturnResult.CANCEL;
    }
  }

  private ReturnResult showYesNoCancel(String resultStr) {
    final int answer = Messages.showYesNoCancelDialog(project,
      resultStr,
      "SonarLint Analysis Results",
      "Review Issues",
      "Commit Anyway",
      "Close",
      UIUtil.getWarningIcon());

    if (answer == Messages.YES) {
      showChangedFilesTab();
      return ReturnResult.CLOSE_WINDOW;
    } else if (answer == Messages.CANCEL) {
      return ReturnResult.CANCEL;
    } else {
      return ReturnResult.COMMIT;
    }
  }

  private void showChangedFilesTab() {
    ToolWindow toolWindow = toolWindowManager.getToolWindow(SONARLINT_TOOL_WINDOW_ID);
    if (toolWindow != null) {
      toolWindow.show(new ChangedFilesTabOpener(toolWindow));
    }
  }

  private static class ChangedFilesTabOpener implements Runnable {
    private final ToolWindow toolWindow;

    private ChangedFilesTabOpener(ToolWindow toolWindow) {
      this.toolWindow = toolWindow;
    }

    @Override public void run() {
      ContentManager contentManager = toolWindow.getContentManager();
      Content content = contentManager.findContent(SonarLintToolWindowFactory.TAB_CHANGED_FILES);
      if (content != null) {
        contentManager.setSelectedContent(content);
      }
    }
  }

  private class MyRefreshableOnComponent implements RefreshableOnComponent {
    private final JCheckBox checkBox;

    private MyRefreshableOnComponent(JCheckBox checkBox) {
      this.checkBox = checkBox;
    }

    @Override
    public JComponent getComponent() {
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(checkBox);
      boolean dumb = DumbService.isDumb(project);
      checkBox.setEnabled(!dumb);
      checkBox.setToolTipText(dumb ? "SonarLint analysis is impossible until indices are up-to-date" : "");
      return panel;
    }

    @Override
    public void refresh() {
      // nothing to do
    }

    @Override
    public void saveState() {
      PropertiesComponent.getInstance(project).setValue(ACTIVATED_OPTION_NAME, checkBox.isSelected());
    }

    @Override
    public void restoreState() {
      PropertiesComponent props = PropertiesComponent.getInstance(project);

      if (!props.isValueSet(ACTIVATED_OPTION_NAME)) {
        checkBox.setSelected(globalSettings.isAutoTrigger());
      } else {
        checkBox.setSelected(props.getBoolean(ACTIVATED_OPTION_NAME));
      }
    }
  }
}
