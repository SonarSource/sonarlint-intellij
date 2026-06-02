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

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.concurrent.CancellationException;
import javax.annotation.Nullable;
import javax.swing.Icon;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.sonarlint.intellij.analysis.AnalysisStatus;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.finding.sca.DependencyRisksCache;
import org.sonarlint.intellij.finding.sca.ScaAnalysisStatus;
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications;
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
    return backendIsAlive && (scaAnalysisRunning || !status.isRunning());
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

    var scaAnalysisStatus = getService(project, ScaAnalysisStatus.class);
    if (scaAnalysisStatus.getRunning()) {
      runOnPooledThread(project, () -> getService(BackendService.class).cancelDependencyRiskAnalysis(project));
      return;
    }

    // Track the running state synchronously here (not only via the backend status notification) so the toolbar
    // flips to the Stop button immediately on click, and reverts once the analysis future settles.
    scaAnalysisStatus.setRunningState(true);
    runOnPooledThread(project, () -> getService(BackendService.class).analyzeDependencyRiskProject(project)
      .thenAcceptAsync(response -> {
        getService(project, DependencyRisksCache.class).replaceWith(response.getDependencyRisks());
        getService(project, SonarLintToolWindow.class).refreshViews();
        getService(project, SonarLintProjectNotifications.class)
          .displaySuccessfulNotification(successMessage(response), NotificationGroupManager.getInstance().getNotificationGroup(GROUP));
      })
      .exceptionally(error -> {
        if (isCancellation(error)) {
          return null;
        }
        SonarLintConsole.get(project).error("Error while analyzing SCA dependencies", error);
        getService(project, SonarLintProjectNotifications.class)
          .displayErrorNotification("Could not analyze SCA dependencies", NotificationGroupManager.getInstance().getNotificationGroup(GROUP));
        return null;
      })
      .whenComplete((unused, throwable) -> scaAnalysisStatus.setRunningState(false)));
  }

  @Override
  protected void updatePresentation(AnActionEvent e, Project project) {
    if (getService(project, ScaAnalysisStatus.class).getRunning()) {
      e.getPresentation().setText("Stop SCA Dependency Analysis");
      e.getPresentation().setDescription("Stop the running SonarQube for IDE SCA dependency analysis");
      e.getPresentation().setIcon(AllIcons.Actions.Suspend);
    } else {
      e.getPresentation().setText("Analyze SCA Dependencies");
      e.getPresentation().setDescription("Run a SonarQube for IDE SCA dependency analysis on the project");
      e.getPresentation().setIcon(AllIcons.Actions.Execute);
    }
  }

  private static String successMessage(AnalyzeDependencyRiskProjectResponse response) {
    return "SCA dependency analysis completed: "
      + response.getDependencyRisks().size() + " dependency risks, "
      + response.getParsedFiles().size() + " parsed files, "
      + response.getErrors().size() + " errors";
  }

  private static boolean isCancellation(Throwable error) {
    // The backend signals cancellation either as a CancellationException or, when the cancel arrives over RPC as a
    // separate request, as a ResponseErrorException with the RequestCancelled code. The identity-based "seen" set
    // stops the walk on cyclic cause chains, which Java allows and would otherwise loop forever.
    var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
    var current = error;
    while (current != null && seen.add(current)) {
      if (current instanceof CancellationException || isRequestCancelled(current)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private static boolean isRequestCancelled(Throwable error) {
    return error instanceof ResponseErrorException responseError
      && responseError.getResponseError() != null
      && responseError.getResponseError().getCode() == ResponseErrorCode.RequestCancelled.getValue();
  }
}
