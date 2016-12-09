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

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBusConnection;

import java.awt.BorderLayout;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;
import javax.swing.Box;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.tree.DefaultTreeModel;

import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.messages.IssueStoreListener;
import org.sonarlint.intellij.messages.StatusListener;
import org.sonarlint.intellij.ui.nodes.IssueNode;
import org.sonarlint.intellij.ui.scope.AbstractScope;
import org.sonarlint.intellij.ui.scope.CurrentFileScope;
import org.sonarlint.intellij.ui.tree.IssueTree;
import org.sonarlint.intellij.ui.tree.TreeModelBuilder;
import org.sonarlint.intellij.util.SonarLintUtils;

public class SonarLintIssuesPanel extends AbstractIssuesPanel implements DataProvider {
  private static final String ID = "SonarLint";
  private static final String GROUP_ID = "SonarLint.issuestoolwindow";
  private static final String SPLIT_PROPORTION_PROPERTY = "SONARLINT_ISSUES_SPLIT_PROPORTION";

  private final IssueManager issueManager;

  private ActionToolbar mainToolbar;
  private AbstractScope scope;
  private SonarLintRulePanel rulePanel;

  public SonarLintIssuesPanel(Project project) {
    this.project = project;
    this.issueManager = project.getComponent(IssueManager.class);
    this.scope = new CurrentFileScope(project);

    ProjectBindingManager projectBindingManager = SonarLintUtils.get(project, ProjectBindingManager.class);

    addToolbar();

    JPanel issuesPanel = new JPanel(new BorderLayout());
    createTree();
    issuesPanel.add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);
    issuesPanel.add(new AutoTriggerStatusPanel(project).getPanel(), BorderLayout.SOUTH);

    rulePanel = new SonarLintRulePanel(project, projectBindingManager);

    JScrollPane scrollableRulePanel = ScrollPaneFactory.createScrollPane(
      rulePanel.getPanel(),
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollableRulePanel.getVerticalScrollBar().setUnitIncrement(10);

    super.setContent(createSplitter(issuesPanel, scrollableRulePanel, SPLIT_PROPORTION_PROPERTY));

    subscribeToEvents();
    updateTree();
  }

  private void subscribeToEvents() {
    scope.addListener(this::updateTree);
    MessageBusConnection busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(IssueStoreListener.SONARLINT_ISSUE_STORE_TOPIC, new IssueStoreListener() {

      @Override public void filesChanged(final Map<VirtualFile, Collection<LiveIssue>> map) {
        ApplicationManager.getApplication().invokeLater(() -> {
          treeBuilder.updateModel(map, "No issues found in the current opened file");
          expandTree();
        });
      }

      @Override public void allChanged() {
        ApplicationManager.getApplication().invokeLater(SonarLintIssuesPanel.this::updateTree);
      }
    });

    busConnection.subscribe(StatusListener.SONARLINT_STATUS_TOPIC,
      newStatus -> ApplicationManager.getApplication().invokeLater(mainToolbar::updateActionsImmediately));
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

  public void updateTree() {
    Map<VirtualFile, Collection<LiveIssue>> issuesPerFile = new HashMap<>();
    Collection<VirtualFile> all = scope.getAll();
    for (VirtualFile f : all) {
      Collection<LiveIssue> issues = issueManager.getForFileOrNull(f);
      if (issues != null) {
        issuesPerFile.put(f, issues);
      }
    }
    String emptyText;
    if (all.isEmpty()) {
      emptyText = "No file opened in the editor";
    } else if (issuesPerFile.isEmpty()) {
      emptyText = "No analysis done on the current opened file";
    } else {
      emptyText = "No issues found in the current opened file";
    }

    treeBuilder.updateModel(issuesPerFile, emptyText);
    expandTree();
  }

  private void expandTree() {
    TreeUtil.expandAll(tree);
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

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (AbstractScope.SCOPE_DATA_KEY.is(dataId)) {
      return scope;
    }

    return null;
  }

}
