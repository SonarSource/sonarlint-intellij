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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangeListAdapter;
import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.tools.SimpleActionGroup;
import com.intellij.util.messages.MessageBusConnection;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import org.sonarlint.intellij.issue.ChangedFilesIssues;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.messages.AnalysisResultsListener;
import org.sonarlint.intellij.util.SonarLintActions;
import org.sonarlint.intellij.util.SonarLintUtils;

public class ChangedFilesScope extends AbstractScope implements Disposable {
  public static final String NAME = "VCS changed files";
  private final Project project;
  private final ChangeListManager changeListManager;
  private final ChangedFilesIssues analysisResultsStore;
  private final ChangeListListener vcsChangeListener = new ChangeListAdapter() {
    @Override
    public void changeListUpdateDone() {
      ApplicationManager.getApplication().invokeLater(ChangedFilesScope.this::updateTexts);
    }
  };
  private SimpleActionGroup actionGroup;

  public ChangedFilesScope(Project project) {
    this.project = project;
    this.analysisResultsStore = SonarLintUtils.get(project, ChangedFilesIssues.class);
    this.changeListManager = SonarLintUtils.get(project, ChangeListManager.class);
    createActionGroup();
    subscribeToEvents();
    Disposer.register(project, this);
  }

  private void createActionGroup() {
    SonarLintActions sonarLintActions = SonarLintActions.getInstance();
    actionGroup = new SimpleActionGroup();
    actionGroup.add(sonarLintActions.analyzeChangedFiles());
    actionGroup.add(sonarLintActions.cancelAnalysis());
    actionGroup.add(sonarLintActions.configure());
    actionGroup.add(sonarLintActions.clearResults());
  }

  private void subscribeToEvents() {
    changeListManager.addChangeListListener(vcsChangeListener);
    MessageBusConnection busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED,
      () -> ApplicationManager.getApplication().invokeLater(this::updateTexts));
    busConnection.subscribe(AnalysisResultsListener.CHANGED_FILES_TOPIC, issues -> ApplicationManager.getApplication().invokeLater(this::updateIssues));
  }

  @Override
  public String getEmptyText() {
    if (analysisResultsStore.wasAnalyzed()) {
      return "No issues in changed files";
    } else if (changeListManager.getAffectedFiles().isEmpty()) {
      return "No changed files in the VCS";
    } else {
      return "No analysis done on changed files";
    }
  }

  @Override
  public String getLabelText() {
    String noAnalysisLabel = "Trigger the analysis to find issues on the files in the VCS change set";
    String noChangedFilesLabel = "VCS contains no changed files";
    String noVcs = "Project has no active VCS";

    if (!hasVcs()) {
      return noVcs;
    } else if (changeListManager.getAffectedFiles().isEmpty()) {
      return noChangedFilesLabel;
    } else {
      return noAnalysisLabel;
    }
  }

  private boolean hasVcs() {
    ProjectLevelVcsManager vcsManager = SonarLintUtils.get(project, ProjectLevelVcsManager.class);
    return vcsManager.hasActiveVcss();
  }

  @Override public ActionGroup toolbarActionGroup() {
    return actionGroup;
  }

  @Override public Instant getLastAnalysisDate() {
    return analysisResultsStore.lastAnalysisDate();
  }

  @Override public Map<VirtualFile, Collection<LiveIssue>> issues() {
    return analysisResultsStore.issues();
  }

  @Override public String getDisplayName() {
    return NAME;
  }

  @Override
  public void dispose() {
    changeListManager.removeChangeListListener(vcsChangeListener);
  }
}
