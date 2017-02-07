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
package org.sonarlint.intellij.ui;

import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.issue.AllFilesIssues;
import org.sonarlint.intellij.messages.AllFilesIssuesListener;
import org.sonarlint.intellij.messages.StatusListener;

public class SonarLintAllFilesPanel extends AbstractIssuesPanel implements OccurenceNavigator, DataProvider {
  private static final String GROUP_ID = "SonarLint.alltoolwindow";
  private static final String SPLIT_PROPORTION_PROPERTY = "SONARLINT_RESULTS_SPLIT_PROPORTION";

  private final LastAnalysisPanel lastAnalysisPanel;
  private final AllFilesIssues allFilesIssues;

  public SonarLintAllFilesPanel(Project project, AllFilesIssues allFilesIssues, ProjectBindingManager projectBindingManager) {
    super(project, projectBindingManager);
    this.allFilesIssues = allFilesIssues;
    this.lastAnalysisPanel = new LastAnalysisPanel(allFilesIssues.lastAnalysisDate(), project,
      () -> "Trigger the analysis to find issues on all source files");

    // Issues panel with tree
    JPanel issuesPanel = new JPanel(new BorderLayout());
    issuesPanel.add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);
    issuesPanel.add(lastAnalysisPanel.getPanel(), BorderLayout.SOUTH);

    // Put everything together
    super.setContent(createSplitter(issuesPanel, detailsTab, SPLIT_PROPORTION_PROPERTY, false, 0.65f));

    // Events
    this.treeBuilder.updateModel(allFilesIssues.issues(), getEmptyText());
    subscribeToEvents();
  }

  private void subscribeToEvents() {
    MessageBusConnection busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(AllFilesIssuesListener.ALL_FILES_ISSUES_TOPIC, issues -> ApplicationManager.getApplication().invokeLater(() -> {
      treeBuilder.updateModel(issues, getEmptyText());
      lastAnalysisPanel.update(allFilesIssues.lastAnalysisDate());
      expandTree();
    }));
    busConnection.subscribe(StatusListener.SONARLINT_STATUS_TOPIC, newStatus ->
      ApplicationManager.getApplication().invokeLater(mainToolbar::updateActionsImmediately));
  }

  private void expandTree() {
    if (treeBuilder.numberIssues() < 30) {
      TreeUtil.expandAll(tree);
    } else {
      tree.expandRow(0);
    }
  }

  private String getEmptyText() {
    if (allFilesIssues.wasAnalyzed()) {
      return "No issues found";
    } else {
      return "No analysis done";
    }
  }

  @Override
  protected String getToolbarGroupId() {
    return GROUP_ID;
  }
}
