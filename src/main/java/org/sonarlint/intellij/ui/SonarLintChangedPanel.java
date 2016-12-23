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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.changes.ChangeListAdapter;
import com.intellij.openapi.vcs.changes.ChangeListListener;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.issue.ChangedFilesIssues;
import org.sonarlint.intellij.messages.ChangedFilesIssuesListener;
import org.sonarlint.intellij.messages.StatusListener;

public class SonarLintChangedPanel extends AbstractIssuesPanel implements OccurenceNavigator, DataProvider, Disposable {
  private static final String GROUP_ID = "SonarLint.changedtoolwindow";
  private static final String SPLIT_PROPORTION_PROPERTY = "SONARLINT_CHANGED_ISSUES_SPLIT_PROPORTION";
  private static final String FLOWS_SPLIT_PROPORTION_PROPERTY = "SONARLINT_CHANGED_ISSUES_FLOWS_SPLIT_PROPORTION";

  private final LastAnalysisPanel lastAnalysisPanel;
  private final ChangedFilesIssues changedFileIssues;
  private final ChangeListManager changeListManager;

  private ChangeListListener vcsChangeListener = new ChangeListAdapter() {
    @Override
    public void changeListUpdateDone() {
      ApplicationManager.getApplication().invokeLater(() -> treeBuilder.updateEmptyText(getEmptyText()));
    }
  };

  public SonarLintChangedPanel(Project project, ChangedFilesIssues changedFileIssues, ProjectBindingManager projectBindingManager) {
    super(project);
    this.changedFileIssues = changedFileIssues;
    this.lastAnalysisPanel = new LastAnalysisPanel(changedFileIssues, project);
    this.changeListManager = ChangeListManager.getInstance(project);

    // Issues panel with tree
    JPanel issuesPanel = new JPanel(new BorderLayout());
    issuesPanel.add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);
    issuesPanel.add(lastAnalysisPanel.getPanel(), BorderLayout.SOUTH);

    // Flows panel with tree
    JScrollPane flowsPanel = ScrollPaneFactory.createScrollPane(flowsTree);
    flowsPanel.getVerticalScrollBar().setUnitIncrement(10);

    // Rule panel
    rulePanel = new SonarLintRulePanel(project, projectBindingManager);
    JScrollPane scrollableRulePanel = ScrollPaneFactory.createScrollPane(
      rulePanel.getPanel(),
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollableRulePanel.getVerticalScrollBar().setUnitIncrement(10);

    // Put everything together
    JComponent rightComponent = createSplitter(scrollableRulePanel, flowsPanel, FLOWS_SPLIT_PROPORTION_PROPERTY, true, 0.5f);
    super.setContent(createSplitter(issuesPanel, rightComponent, SPLIT_PROPORTION_PROPERTY, false, 0.65f));

    // Events
    this.treeBuilder.updateModel(changedFileIssues.issues(), getEmptyText());
    subscribeToEvents();
    Disposer.register(project, this);
  }

  private void subscribeToEvents() {
    changeListManager.addChangeListListener(vcsChangeListener);
    MessageBusConnection busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(ChangedFilesIssuesListener.CHANGED_FILES_ISSUES_TOPIC, issues -> ApplicationManager.getApplication().invokeLater(() -> {
      treeBuilder.updateModel(issues, getEmptyText());
      lastAnalysisPanel.update();
      expandTree();
    }));
    busConnection.subscribe(StatusListener.SONARLINT_STATUS_TOPIC, newStatus ->
      ApplicationManager.getApplication().invokeLater(mainToolbar::updateActionsImmediately));
  }

  private void expandTree() {
    if (tree.getRowCount() < 30) {
      TreeUtil.expandAll(tree);
    } else {
      tree.expandRow(0);
      if (tree.getRowCount() > 1) {
        tree.expandRow(1);
      }
    }
  }

  private String getEmptyText() {
    if (changedFileIssues.wasAnalyzed()) {
      return "No issues in changed files";
    } else if (changeListManager.getAffectedFiles().isEmpty()) {
      return "No changed files in the VCS";
    } else {
      return "No analysis done on changed files";
    }
  }

  @Override
  protected String getToolbarGroupId() {
    return GROUP_ID;
  }

  @Override
  public void dispose() {
    changeListManager.removeChangeListListener(vcsChangeListener);
  }
}
