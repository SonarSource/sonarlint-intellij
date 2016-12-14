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
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
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
import javax.swing.Box;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.tree.DefaultTreeModel;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.issue.ChangedFilesIssues;
import org.sonarlint.intellij.messages.ChangedFilesIssuesListener;
import org.sonarlint.intellij.messages.StatusListener;
import org.sonarlint.intellij.ui.nodes.IssueNode;
import org.sonarlint.intellij.ui.tree.IssueTree;
import org.sonarlint.intellij.ui.tree.TreeModelBuilder;
import org.sonarlint.intellij.util.SonarLintUtils;

public class SonarLintChangedPanel extends AbstractIssuesPanel implements OccurenceNavigator, DataProvider, Disposable {
  private static final String ID = "SonarLint";
  private static final String GROUP_ID = "SonarLint.changedtoolwindow";
  private static final String SPLIT_PROPORTION_PROPERTY = "SONARLINT_CHANGED_ISSUES_SPLIT_PROPORTION";

  private final SonarLintRulePanel rulePanel;
  private final LastAnalysisPanel lastAnalysisPanel;
  private final ChangedFilesIssues changedFileIssues;
  private final ChangeListManager changeListManager;
  private ActionToolbar mainToolbar;

  private ChangeListListener vcsChangeListener = new ChangeListAdapter() {
    @Override
    public void changeListUpdateDone() {
      ApplicationManager.getApplication().invokeLater(() -> treeBuilder.updateEmptyText(getEmptyText()));
    }
  };

  public SonarLintChangedPanel(Project project, ChangedFilesIssues changedFileIssues) {
    this.changedFileIssues = changedFileIssues;
    this.project = project;
    this.lastAnalysisPanel = new LastAnalysisPanel(changedFileIssues, project);
    this.changeListManager = ChangeListManager.getInstance(project);
    ProjectBindingManager projectBindingManager = SonarLintUtils.get(project, ProjectBindingManager.class);
    addToolbar();

    JPanel issuesPanel = new JPanel(new BorderLayout());
    createTree();
    issuesPanel.add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);
    issuesPanel.add(lastAnalysisPanel.getPanel(), BorderLayout.SOUTH);

    rulePanel = new SonarLintRulePanel(project, projectBindingManager);

    JScrollPane scrollableRulePanel = ScrollPaneFactory.createScrollPane(
      rulePanel.getPanel(),
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollableRulePanel.getVerticalScrollBar().setUnitIncrement(10);

    super.setContent(createSplitter(issuesPanel, scrollableRulePanel, SPLIT_PROPORTION_PROPERTY));
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

  private void addToolbar() {
    ActionGroup mainActionGroup = (ActionGroup) ActionManager.getInstance().getAction(GROUP_ID);
    mainToolbar = ActionManager.getInstance().createActionToolbar(ID, mainActionGroup, false);
    mainToolbar.setTargetComponent(this);
    Box toolBarBox = Box.createHorizontalBox();
    toolBarBox.add(mainToolbar.getComponent());

    super.setToolbar(toolBarBox);
    mainToolbar.getComponent().setVisible(true);
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

  private void issueTreeSelectionChanged() {
    IssueNode[] selectedNodes = tree.getSelectedNodes(IssueNode.class, null);
    if (selectedNodes.length > 0) {
      rulePanel.setRuleKey(selectedNodes[0].issue());
    } else {
      rulePanel.setRuleKey(null);
    }
  }

  private void createTree() {
    treeBuilder = new TreeModelBuilder();
    DefaultTreeModel model = treeBuilder.createModel();
    tree = new IssueTree(project, model);
    tree.addTreeSelectionListener(e -> issueTreeSelectionChanged());
  }

  @Override
  public void dispose() {
    changeListManager.removeChangeListListener(vcsChangeListener);
  }
}
