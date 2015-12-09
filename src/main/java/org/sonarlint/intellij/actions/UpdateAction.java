/**
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
package org.sonarlint.intellij.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.analysis.SonarLintStatus;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.analysis.SonarQubeRunnerFacade;

import javax.swing.*;

public class UpdateAction extends AbstractSonarAction {

  public static final String TITLE = "SonarLint update";

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project p = e.getProject();
    final SonarLintStatus status = SonarLintStatus.get(p);
    final SonarLintConsole console = SonarLintConsole.getSonarQubeConsole(p);

    if (!status.tryRun()) {
      String msg = "Unable to update SonarLint as an analysis is on-going";
      console.error(msg);
      showMessage(p, msg, Messages.getErrorIcon());
      return;
    }

    ProgressManager.getInstance().run(new Task.Backgroundable(e.getProject(), "Update SonarLint") {
      @Override
      public void run(@NotNull ProgressIndicator progressIndicator) {
        doUpdate(p, status, console);
      }
    });
  }

  @Override
  protected boolean isEnabled(SonarLintStatus status) {
    return !status.isRunning();
  }

  void doUpdate(final Project p, final SonarLintStatus status, final SonarLintConsole console) {
    SonarQubeRunnerFacade runner = p.getComponent(SonarQubeRunnerFacade.class);

    try {
      runner.tryUpdate();
    } catch (final Exception ex) {
      console.error("Unable to perform update", ex);
      showMessage(p, "Unable to update SonarLint: " + ex.getMessage(), Messages.getErrorIcon());
      return;
    } finally {
      status.stopRun();
    }

    String version = runner.getVersion();
    if (version == null) {
      showMessage(p, "Unable to update SonarLint. Please check logs in SonarLint console.", Messages.getErrorIcon());
    } else {
      showMessage(p, "SonarLint is up to date and running", Messages.getInformationIcon());
    }
  }

  private static void showMessage(final Project p, final String msg, final Icon icon) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        Messages.showMessageDialog(p, msg, TITLE, icon);
      }
    });
  }
}
