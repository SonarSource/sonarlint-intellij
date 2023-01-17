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

import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.tools.SimpleActionGroup;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Collection;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.swing.Box;
import javax.swing.ScrollPaneConstants;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.editor.EditorDecorator;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.ui.nodes.AbstractNode;
import org.sonarlint.intellij.ui.nodes.IssueNode;
import org.sonarlint.intellij.ui.tree.FlowsTree;
import org.sonarlint.intellij.ui.tree.FlowsTreeModelBuilder;
import org.sonarlint.intellij.ui.tree.IssueTree;
import org.sonarlint.intellij.ui.tree.IssueTreeModelBuilder;

public abstract class AbstractIssuesPanel extends SimpleToolWindowPanel implements OccurenceNavigator {
  private static final String ID = "SonarLint";
  private static final int RULE_TAB_INDEX = 0;
  private static final int LOCATIONS_TAB_INDEX = 1;
  protected final Project project;
  protected SonarLintRulePanel rulePanel;
  protected JBTabbedPane detailsTab;
  protected Tree tree;
  protected IssueTreeModelBuilder treeBuilder;
  protected FlowsTree flowsTree;
  protected FlowsTreeModelBuilder flowsTreeBuilder;
  private ActionToolbar mainToolbar;

  protected AbstractIssuesPanel(Project project) {
    super(false, true);
    this.project = project;

    createFlowsTree();
    createIssuesTree();
    createTabs();
  }

  public void refreshToolbar() {
    mainToolbar.updateActionsImmediately();
  }

  private void createTabs() {
    // Flows panel with tree
    var flowsPanel = ScrollPaneFactory.createScrollPane(flowsTree, true);
    flowsPanel.getVerticalScrollBar().setUnitIncrement(10);

    // Rule panel
    rulePanel = new SonarLintRulePanel(project);

    detailsTab = new JBTabbedPane();
    detailsTab.insertTab("Rule", null, rulePanel, "Details about the rule", RULE_TAB_INDEX);
    detailsTab.insertTab("Locations", null, flowsPanel, "All locations involved in the issue", LOCATIONS_TAB_INDEX);
  }

  protected void issueTreeSelectionChanged() {
    var selectedNodes = tree.getSelectedNodes(IssueNode.class, null);
    if (selectedNodes.length > 0) {
      var issue = selectedNodes[0].issue();
      var moduleForFile = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(issue.psiFile().getVirtualFile());
      rulePanel.setRuleKey(moduleForFile, issue.getRuleKey(), null);
      SonarLintUtils.getService(project, EditorDecorator.class).highlightIssue(issue);
      flowsTree.getEmptyText().setText("Selected issue doesn't have flows");
      flowsTreeBuilder.populateForIssue(issue);
      flowsTree.expandAll();
    } else {
      flowsTreeBuilder.clearFlows();
      flowsTree.getEmptyText().setText("No issue selected");
      rulePanel.setRuleKey(null, null, null);
      var highlighting = SonarLintUtils.getService(project, EditorDecorator.class);
      highlighting.removeHighlights();
    }
  }

  protected void setToolbar(Collection<AnAction> actions) {
    setToolbar(createActionGroup(actions));
  }

  protected void setToolbar(ActionGroup group) {
    if (mainToolbar != null) {
      mainToolbar.setTargetComponent(null);
      super.setToolbar(null);
      mainToolbar = null;
    }
    mainToolbar = ActionManager.getInstance().createActionToolbar(ID, group, false);
    mainToolbar.setTargetComponent(this);
    var toolBarBox = Box.createHorizontalBox();
    toolBarBox.add(mainToolbar.getComponent());
    super.setToolbar(toolBarBox);
    mainToolbar.getComponent().setVisible(true);
  }

  private static ActionGroup createActionGroup(Collection<AnAction> actions) {
    var actionGroup = new SimpleActionGroup();
    actions.forEach(actionGroup::add);
    return actionGroup;
  }

  private void createFlowsTree() {
    flowsTreeBuilder = new FlowsTreeModelBuilder();
    var model = flowsTreeBuilder.createModel();
    flowsTree = new FlowsTree(project, model);
    flowsTreeBuilder.clearFlows();
    flowsTree.getEmptyText().setText("No issue selected");
  }

  private void createIssuesTree() {
    treeBuilder = new IssueTreeModelBuilder();
    var model = treeBuilder.createModel();
    tree = new IssueTree(project, model);
    tree.addTreeSelectionListener(e -> issueTreeSelectionChanged());
    tree.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_ESCAPE == e.getKeyCode()) {
          var highlighting = SonarLintUtils.getService(project, EditorDecorator.class);
          highlighting.removeHighlights();
        }
      }
    });
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
  }

  @CheckForNull
  private OccurenceNavigator.OccurenceInfo occurrence(@Nullable IssueNode node) {
    if (node == null) {
      return null;
    }

    var path = new TreePath(node.getPath());
    tree.getSelectionModel().setSelectionPath(path);
    tree.scrollPathToVisible(path);

    var range = node.issue().getRange();
    var startOffset = (range != null) ? range.getStartOffset() : 0;
    return new OccurenceNavigator.OccurenceInfo(
      new OpenFileDescriptor(project, node.issue().psiFile().getVirtualFile(), startOffset),
      -1,
      -1);
  }

  @Override
  public boolean hasNextOccurence() {
    // relies on the assumption that a TreeNodes will always be the last row in the table view of the tree
    var path = tree.getSelectionPath();
    if (path == null) {
      return false;
    }
    var node = (DefaultMutableTreeNode) path.getLastPathComponent();
    if (node instanceof IssueNode) {
      return tree.getRowCount() != tree.getRowForPath(path) + 1;
    } else {
      return node.getChildCount() > 0;
    }
  }

  @Override
  public boolean hasPreviousOccurence() {
    var path = tree.getSelectionPath();
    if (path == null) {
      return false;
    }
    var node = (DefaultMutableTreeNode) path.getLastPathComponent();
    return (node instanceof IssueNode) && !isFirst(node);
  }

  private static boolean isFirst(final TreeNode node) {
    final var parent = node.getParent();
    return parent == null || (parent.getIndex(node) == 0 && isFirst(parent));
  }

  @CheckForNull
  @Override
  public OccurenceNavigator.OccurenceInfo goNextOccurence() {
    var path = tree.getSelectionPath();
    if (path == null) {
      return null;
    }
    return occurrence(treeBuilder.getNextIssue((AbstractNode) path.getLastPathComponent()));
  }

  @CheckForNull
  @Override
  public OccurenceNavigator.OccurenceInfo goPreviousOccurence() {
    var path = tree.getSelectionPath();
    if (path == null) {
      return null;
    }
    return occurrence(treeBuilder.getPreviousIssue((AbstractNode) path.getLastPathComponent()));
  }

  @Override
  @NotNull
  public String getNextOccurenceActionName() {
    return "Next Issue";
  }

  @Override
  @NotNull
  public String getPreviousOccurenceActionName() {
    return "Previous Issue";
  }

  public void setSelectedIssue(LiveIssue issue) {
    var issueNode = TreeUtil.findNode(((DefaultMutableTreeNode) tree.getModel().getRoot()),
      node -> node instanceof IssueNode && ((IssueNode) node).issue().equals(issue));
    if (issueNode == null) {
      return;
    }
    tree.setSelectionPath(null);
    tree.addSelectionPath(new TreePath(issueNode.getPath()));
  }

  public void selectLocationsTab() {
    detailsTab.setSelectedIndex(LOCATIONS_TAB_INDEX);
  }

  public void selectRulesTab() {
    detailsTab.setSelectedIndex(RULE_TAB_INDEX);
  }

}
