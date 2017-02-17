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
package org.sonarlint.intellij.ui.scope;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.tools.SimpleActionGroup;
import com.intellij.util.messages.MessageBusConnection;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import org.sonarlint.intellij.issue.AllFilesIssues;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.messages.AnalysisResultsListener;
import org.sonarlint.intellij.util.SonarLintActions;
import org.sonarlint.intellij.util.SonarLintUtils;

public class AllFilesScope extends AbstractScope {
  private final Project project;
  private final AllFilesIssues allFilesIssues;
  private SimpleActionGroup actionGroup;

  public AllFilesScope(Project project) {
    this.project = project;
    this.allFilesIssues = SonarLintUtils.get(project, AllFilesIssues.class);
    createActionGroup();
    subscribeToEvents();
  }

  private void createActionGroup() {
    SonarLintActions sonarLintActions = SonarLintActions.getInstance();
    actionGroup = new SimpleActionGroup();
    actionGroup.add(sonarLintActions.analyzeAllFiles());
    actionGroup.add(sonarLintActions.cancelAnalysis());
    actionGroup.add(sonarLintActions.configure());
    actionGroup.add(sonarLintActions.clearResults());
  }

  private void subscribeToEvents() {
    MessageBusConnection busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(AnalysisResultsListener.ALL_FILES_TOPIC, issues -> ApplicationManager.getApplication().invokeLater(this::updateIssues));
  }

  @Override public String getDisplayName() {
    return "All project files";
  }

  @Override public String getEmptyText() {
    if (allFilesIssues.wasAnalyzed()) {
      return "No issues found";
    } else {
      return "No analysis done";
    }
  }

  @Override
  public String getLabelText() {
    return "Trigger the analysis to find issues in all project sources";
  }

  @Override public ActionGroup toolbarActionGroup() {
    return actionGroup;
  }

  @Override public Instant getLastAnalysisDate() {
    return allFilesIssues.lastAnalysisDate();
  }

  @Override public Map<VirtualFile, Collection<LiveIssue>> issues() {
    return allFilesIssues.issues();
  }
}
