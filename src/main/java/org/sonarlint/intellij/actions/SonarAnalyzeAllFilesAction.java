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
package org.sonarlint.intellij.actions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.analysis.AnalysisStatus;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.core.BackendService;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.util.ThreadUtilsKt.runOnPooledThread;

public class SonarAnalyzeAllFilesAction extends AbstractSonarAction {
  private static final String HIDE_WARNING_PROPERTY = "SonarLint.analyzeAllFiles.hideWarning";
  public static final String WARNING_MESSAGE = "Analysing all files may take a considerable amount of time to complete.\n"
    + "To get the best from SonarQube for IDE, you should preferably use the automatic analysis of the file you're working on.";

  public SonarAnalyzeAllFilesAction() {
    super();
  }

  public SonarAnalyzeAllFilesAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Override
  protected boolean isEnabled(AnActionEvent e, Project project, AnalysisStatus status) {
    var backendIsAlive = getService(BackendService.class).isAlive();
    return !status.isRunning() && backendIsAlive;
  }

  @Override
  protected boolean isVisible(AnActionEvent e) {
    return !ActionPlaces.PROJECT_VIEW_POPUP.equals(e.getPlace());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    var project = e.getProject();

    if (project == null || ActionPlaces.PROJECT_VIEW_POPUP.equals(e.getPlace()) || !userConfirmed(project)) {
      return;
    }

    runOnPooledThread(project, () -> SonarLintUtils.getService(project, AnalysisSubmitter.class).analyzeAllFiles());
  }

  static boolean userConfirmed(Project project) {
    if (!ApplicationManager.getApplication().isUnitTestMode() && !PropertiesComponent.getInstance().getBoolean(HIDE_WARNING_PROPERTY,
      false)) {
      return MessageDialogBuilder.yesNo("SonarQube for IDE - Analyze All Files", WARNING_MESSAGE)
        .yesText("Proceed")
        .noText("Cancel")
        .icon(Messages.getWarningIcon())
        .doNotAsk(new DoNotShowAgain())
        .ask(project);
    }
    return true;
  }

  static class DoNotShowAgain implements DoNotAskOption {
    @Override
    public boolean isToBeShown() {
      return true;
    }

    @Override
    public void setToBeShown(boolean toBeShown, int exitCode) {
      PropertiesComponent.getInstance().setValue(HIDE_WARNING_PROPERTY, Boolean.toString(!toBeShown));
    }

    @Override
    public boolean canBeHidden() {
      return true;
    }

    @Override
    public boolean shouldSaveOptionsOnCancel() {
      return false;
    }

    @NotNull
    @Override
    public String getDoNotShowMessage() {
      return "Don't show again";
    }
  }
}
