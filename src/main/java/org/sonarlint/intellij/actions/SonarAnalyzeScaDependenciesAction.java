/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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

import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import java.util.concurrent.CancellationException;
import javax.annotation.Nullable;
import javax.swing.Icon;
import org.sonarlint.intellij.analysis.AnalysisStatus;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.finding.sca.ScaAnalysisStatus;
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications;
import org.sonarlint.intellij.ui.icons.SonarLintIcons;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.AnalyzeDependencyRiskProjectResponse;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.util.ThreadUtilsKt.runOnPooledThread;

public class SonarAnalyzeScaDependenciesAction extends AbstractSonarAction {

  private static final String GROUP = "SonarQube for IDE";

  public SonarAnalyzeScaDependenciesAction() {
    super();
  }

  public SonarAnalyzeScaDependenciesAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Override
  protected boolean isEnabled(AnActionEvent e, Project project, AnalysisStatus status) {
    var backendIsAlive = getService(BackendService.class).isAlive();
    var scaAnalysisRunning = getService(project, ScaAnalysisStatus.class).getRunning();
    return backendIsAlive && !scaAnalysisRunning && !status.isRunning();
  }

  @Override
  protected boolean isVisible(AnActionEvent e) {
    return !ActionPlaces.PROJECT_VIEW_POPUP.equals(e.getPlace());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    var project = e.getProject();
    if (project == null || project.isDisposed() || ActionPlaces.PROJECT_VIEW_POPUP.equals(e.getPlace())) {
      return;
    }

    runOnPooledThread(project, () -> {
      var scaAnalysisStatus = getService(project, ScaAnalysisStatus.class);
      scaAnalysisStatus.setRunningState(true);
      getService(BackendService.class).analyzeDependencyRiskProject(project)
        .whenCompleteAsync((response, error) -> {
          scaAnalysisStatus.setRunningState(false);
          if (error == null) {
            getService(project, SonarLintProjectNotifications.class)
              .displaySuccessfulNotification(successMessage(response), NotificationGroupManager.getInstance().getNotificationGroup(GROUP));
            return;
          }
          if (isCancellation(error)) {
            return;
          }
          SonarLintConsole.get(project).error("Error while analyzing SCA dependencies", error);
          getService(project, SonarLintProjectNotifications.class)
            .displayErrorNotification("Could not analyze SCA dependencies", NotificationGroupManager.getInstance().getNotificationGroup(GROUP));
        });
    });
  }

  @Override
  protected void updatePresentation(AnActionEvent e, Project project) {
    if (getService(project, ScaAnalysisStatus.class).getRunning()) {
      e.getPresentation().setText("Analyze SCA Dependencies");
      e.getPresentation().setDescription("Run a SonarQube for IDE SCA dependency analysis on the project");
      e.getPresentation().setIcon(SonarLintIcons.PLAY);
    } else {
      e.getPresentation().setText("Analyze SCA Dependencies");
      e.getPresentation().setDescription("Run a SonarQube for IDE SCA dependency analysis on the project");
      e.getPresentation().setIcon(SonarLintIcons.PLAY);
    }
  }

  private static String successMessage(AnalyzeDependencyRiskProjectResponse response) {
    return "SCA dependency analysis completed: "
      + response.getDependencyRisks().size() + " dependency risks, "
      + response.getParsedFiles().size() + " parsed files, "
      + response.getErrors().size() + " errors";
  }

  private static boolean isCancellation(Throwable error) {
    var current = error;
    while (current != null) {
      if (current instanceof CancellationException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
