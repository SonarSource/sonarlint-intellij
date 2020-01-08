/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.tools.SimpleActionGroup;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.BorderLayout;
import javax.swing.JPanel;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.messages.AnalysisResultsListener;
import org.sonarlint.intellij.messages.StatusListener;
import org.sonarlint.intellij.util.SonarLintActions;

public class SonarLintAnalysisResultsPanel extends AbstractIssuesPanel {
  private static final String SPLIT_PROPORTION_PROPERTY = "SONARLINT_ANALYSIS_RESULTS_SPLIT_PROPORTION";

  private final LastAnalysisPanel lastAnalysisPanel;
  private final AnalysisResults results;

  public SonarLintAnalysisResultsPanel(Project project, ProjectBindingManager projectBindingManager) {
    super(project, projectBindingManager);
    this.lastAnalysisPanel = new LastAnalysisPanel(project);
    this.results = new AnalysisResults(project);

    // Issues panel with tree
    JPanel issuesPanel = new JPanel(new BorderLayout());
    issuesPanel.add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);
    issuesPanel.add(lastAnalysisPanel.getPanel(), BorderLayout.SOUTH);
    setToolbar(createActionGroup());

    // Put everything together
    super.setContent(createSplitter(issuesPanel, detailsTab, SPLIT_PROPORTION_PROPERTY, false, 0.65f));

    // Subscribe to events
    subscribeToEvents();
  }

  private static SimpleActionGroup createActionGroup() {
    SonarLintActions sonarLintActions = SonarLintActions.getInstance();
    SimpleActionGroup actionGroup = new SimpleActionGroup();
    actionGroup.add(sonarLintActions.analyzeChangedFiles());
    actionGroup.add(sonarLintActions.analyzeAllFiles());
    actionGroup.add(sonarLintActions.cancelAnalysis());
    actionGroup.add(sonarLintActions.configure());
    actionGroup.add(sonarLintActions.clearResults());
    return actionGroup;
  }

  private void subscribeToEvents() {
    MessageBusConnection busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(StatusListener.SONARLINT_STATUS_TOPIC, newStatus -> ApplicationManager.getApplication().invokeLater(this::refreshToolbar));
    busConnection.subscribe(AnalysisResultsListener.ANALYSIS_RESULTS_TOPIC, issues -> ApplicationManager.getApplication().invokeLater(this::updateIssues));

  }

  public void updateIssues() {
    lastAnalysisPanel.update(results.getLastAnalysisDate(), results.whatAnalyzed(), results.getLabelText());
    treeBuilder.updateModel(results.issues(), results.getEmptyText());
    expandTree();
  }

  private void expandTree() {
    if (treeBuilder.numberIssues() < 30) {
      TreeUtil.expandAll(tree);
    } else {
      tree.expandRow(0);
    }
  }
}
