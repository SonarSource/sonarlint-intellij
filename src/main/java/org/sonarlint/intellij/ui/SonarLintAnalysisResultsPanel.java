/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.tools.SimpleActionGroup;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.BorderLayout;
import java.util.Optional;
import javax.swing.JPanel;
import org.sonarlint.intellij.messages.AnalysisResultsListener;
import org.sonarlint.intellij.messages.StatusListener;
import org.sonarlint.intellij.util.SonarLintActions;

import static org.sonarlint.intellij.ui.SonarLintToolWindowFactory.createSplitter;

public class SonarLintAnalysisResultsPanel extends AbstractIssuesPanel implements Disposable {
  private static final String SPLIT_PROPORTION_PROPERTY = "SONARLINT_ANALYSIS_RESULTS_SPLIT_PROPORTION";

  private final LastAnalysisPanel lastAnalysisPanel;
  private final AnalysisResults results;

  public SonarLintAnalysisResultsPanel(Project project) {
    super(project);
    this.lastAnalysisPanel = new LastAnalysisPanel();
    this.results = new AnalysisResults(project);

    // Issues panel with tree
    var issuesPanel = new JPanel(new BorderLayout());
    issuesPanel.add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);
    issuesPanel.add(lastAnalysisPanel.getPanel(), BorderLayout.SOUTH);
    setToolbar(createActionGroup());

    // Put everything together
    super.setContent(createSplitter(project, this, this, issuesPanel, detailsTab, SPLIT_PROPORTION_PROPERTY, 0.5f));

    // Subscribe to events
    subscribeToEvents();
  }

  private static SimpleActionGroup createActionGroup() {
    var sonarLintActions = SonarLintActions.getInstance();
    var actionGroup = new SimpleActionGroup();
    actionGroup.add(sonarLintActions.analyzeChangedFiles());
    actionGroup.add(sonarLintActions.analyzeAllFiles());
    actionGroup.add(sonarLintActions.cancelAnalysis());
    actionGroup.add(sonarLintActions.configure());
    actionGroup.add(sonarLintActions.clearResults());
    return actionGroup;
  }

  private void subscribeToEvents() {
    var busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(StatusListener.SONARLINT_STATUS_TOPIC, newStatus -> ApplicationManager.getApplication().invokeLater(this::refreshToolbar));
    busConnection.subscribe(AnalysisResultsListener.ANALYSIS_RESULTS_TOPIC, issues -> ApplicationManager.getApplication().invokeLater(this::updateIssues));

  }

  public void updateIssues() {
    if (project.isDisposed()) {
      return;
    }
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

  @Override
  // called automatically because the panel is one of the content of the tool window
  public void dispose() {
    lastAnalysisPanel.dispose();
  }

  public void showDuplicationDensity(Optional<Float> duplicationDensityThreshold, float density) {
    treeBuilder.showDuplicationDensity(duplicationDensityThreshold, density);
  }
}
