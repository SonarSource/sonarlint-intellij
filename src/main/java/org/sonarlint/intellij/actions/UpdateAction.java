/**
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
 * sonarqube@googlegroups.com
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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.console.SonarLintConsole;
import org.sonarlint.intellij.inspection.SonarQubeRunnerFacade;

public class UpdateAction extends AnAction {

  public static final String TITLE = "SonarLint update";

  @Override
  public void actionPerformed(final AnActionEvent e) {
    ProgressManager.getInstance().run(new Task.Backgroundable(e.getProject(), "Update SonarLint") {
      @Override
      public void run(@NotNull ProgressIndicator progressIndicator) {
        doUpdate(e);
      }
    });
  }

  void doUpdate(final AnActionEvent e) {
    final SonarQubeRunnerFacade runner = e.getProject().getComponent(SonarQubeRunnerFacade.class);
    try {
      runner.tryUpdate();
    } catch (final Exception ex) {
      SonarLintConsole.getSonarQubeConsole(e.getProject()).error("Unable to perform update", ex);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          Messages.showMessageDialog(e.getProject(), "Unable to update SonarLint: " + ex.getMessage(), TITLE, Messages.getErrorIcon());
        }
      });
      return;
    }

    final String version = runner.getVersion();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (version == null) {
          Messages.showMessageDialog(e.getProject(), "Unable to update SonarLint. Please check logs in SonarLint console.", TITLE, Messages.getErrorIcon());
        } else {
          Messages.showMessageDialog(e.getProject(), "SonarLint is up to date and running", TITLE, Messages.getInformationIcon());
        }
      }
    });
  }
}
