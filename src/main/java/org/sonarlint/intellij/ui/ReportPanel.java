/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
import javax.swing.JPanel;
import org.sonarlint.intellij.analysis.AnalysisResult;
import org.sonarlint.intellij.messages.StatusListener;
import org.sonarlint.intellij.util.SonarLintActions;

import static org.sonarlint.intellij.ui.SonarLintToolWindowFactory.createSplitter;

public class ReportPanel extends AbstractIssuesPanel implements Disposable {
  private static final String SPLIT_PROPORTION_PROPERTY = "SONARLINT_ANALYSIS_RESULTS_SPLIT_PROPORTION";

  private final LastAnalysisPanel lastAnalysisPanel;

  public ReportPanel(Project project) {
    super(project);
    this.lastAnalysisPanel = new LastAnalysisPanel();

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
    actionGroup.add(sonarLintActions.clearReport());
    return actionGroup;
  }

  private void subscribeToEvents() {
    var busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(StatusListener.SONARLINT_STATUS_TOPIC, newStatus -> ApplicationManager.getApplication().invokeLater(this::refreshToolbar));
  }

  public void updateIssues(AnalysisResult analysisResult) {
    if (project.isDisposed()) {
      return;
    }
    lastAnalysisPanel.update(analysisResult.getAnalysisDate(), analysisResult.getWhatAnalyzed());
    treeBuilder.updateModel(analysisResult.getIssuesPerFile(), "No issues found");
    expandTree();
  }

  public void clear() {
    if (project.isDisposed()) {
      return;
    }
    lastAnalysisPanel.clear();
    treeBuilder.clear();
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
}
