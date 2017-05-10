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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.tree.TreeUtil;
import org.sonarlint.intellij.actions.SonarAnalyzeServerIssuesAction;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.issue.ServerIssues;
import org.sonarlint.intellij.messages.ServerIssuesListener;
import org.sonarlint.intellij.messages.StatusListener;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SonarLintServerPanel extends AbstractIssuesPanel implements DataProvider {
  private static final String SPLIT_PROPORTION_PROPERTY = "SONARLINT_SERVER_ISSUES_SPLIT_PROPORTION";

  private final ServerAnalysisPanel serverAnalysisPanel;
  private final ServerIssues serverIssues;

  public SonarLintServerPanel(Project project, ServerIssues serverIssues, ProjectBindingManager projectBindingManager) {
    super(project, projectBindingManager);

    // Server issues panel
    setToolbar(actions());
    this.serverIssues = serverIssues;
    this.serverAnalysisPanel = new ServerAnalysisPanel(serverIssues, project);

    // Issues panel with tree
    JPanel issuesPanel = new JPanel(new BorderLayout());
    issuesPanel.add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);
    issuesPanel.add(serverAnalysisPanel.getPanel(), BorderLayout.SOUTH);

    // Put everything together
    super.setContent(createSplitter(issuesPanel, detailsTab, SPLIT_PROPORTION_PROPERTY, false, 0.65f));

    // Events
    this.treeBuilder.updateModel(serverIssues.issues(), getEmptyText());
    subscribeToEvents();

    // On start update server files
    SonarAnalyzeServerIssuesAction.analyzeServerIssues(project);
  }

  private static Collection<AnAction> actions() {
    List<AnAction> list = new ArrayList<>();
    list.add(ActionManager.getInstance().getAction("SonarLint.SonarAnalyzeServerIssuesAction"));
    list.add(ActionManager.getInstance().getAction("SonarLint.toolwindow.Configure"));
    list.add(ActionManager.getInstance().getAction("SonarLint.toolwindow.ClearServerIssues"));
    return list;
  }

  private void subscribeToEvents() {
    MessageBusConnection busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(ServerIssuesListener.SERVER_ISSUES_TOPIC, issues -> ApplicationManager.getApplication().invokeLater(() -> {
      treeBuilder.updateModel(issues, getEmptyText());
      serverAnalysisPanel.update();
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
    if (serverIssues.wasAnalyzed()) {
      return "No issues in server issues";
    } else {
      return "No analysis done on server issues";
    }
  }

}
