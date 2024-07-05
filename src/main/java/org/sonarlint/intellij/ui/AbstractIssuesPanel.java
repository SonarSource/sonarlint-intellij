/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Collection;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.swing.Box;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.editor.EditorDecorator;
import org.sonarlint.intellij.finding.Finding;
import org.sonarlint.intellij.finding.LiveFinding;
import org.sonarlint.intellij.finding.ShowFinding;
import org.sonarlint.intellij.finding.TextRangeMatcher;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications;
import org.sonarlint.intellij.ui.nodes.AbstractNode;
import org.sonarlint.intellij.ui.nodes.IssueNode;
import org.sonarlint.intellij.ui.tree.IssueTree;
import org.sonarlint.intellij.ui.tree.IssueTreeModelBuilder;

import static org.sonarlint.intellij.common.ui.ReadActionUtils.computeReadActionSafely;
import static org.sonarlint.intellij.ui.UiUtils.runOnUiThread;
import static org.sonarlint.intellij.util.ThreadUtilsKt.runOnPooledThread;

public abstract class AbstractIssuesPanel extends SimpleToolWindowPanel implements Disposable, OccurenceNavigator {
  private static final String ID = "SonarLint";
  protected final Project project;
  protected Tree tree;
  protected Tree oldTree;
  protected IssueTreeModelBuilder treeBuilder;
  protected IssueTreeModelBuilder oldTreeBuilder;
  protected FindingDetailsPanel findingDetailsPanel;
  private ActionToolbar mainToolbar;

  protected AbstractIssuesPanel(Project project) {
    super(false, true);
    this.project = project;

    createIssuesTree();
    createOldIssuesTree();
    createFindingDetailsPanel();
    handleListener();
  }

  public void refreshToolbar() {
    mainToolbar.updateActionsImmediately();
  }

  private void createFindingDetailsPanel() {
    findingDetailsPanel = new FindingDetailsPanel(project, this, FindingKind.ISSUE);
  }

  private void issueTreeSelectionChanged() {
    var selectedNodes = tree.getSelectedNodes(IssueNode.class, null);
    if (selectedNodes.length > 0) {
      var issue = selectedNodes[0].issue();
      findingDetailsPanel.show(issue);
    } else {
      findingDetailsPanel.clear();
      var highlighting = SonarLintUtils.getService(project, EditorDecorator.class);
      highlighting.removeHighlights();
    }
  }

  private void oldIssueTreeSelectionChanged() {
    var selectedNodes = oldTree.getSelectedNodes(IssueNode.class, null);
    if (selectedNodes.length > 0) {
      var issue = selectedNodes[0].issue();
      findingDetailsPanel.show(issue);
    } else {
      findingDetailsPanel.clear();
      var highlighting = SonarLintUtils.getService(project, EditorDecorator.class);
      highlighting.removeHighlights();
    }
  }

  protected void setToolbar(Collection<AnAction> actions) {
    if (mainToolbar != null) {
      mainToolbar.setTargetComponent(null);
      super.setToolbar(null);
      mainToolbar = null;
    }
    mainToolbar = ActionManager.getInstance().createActionToolbar(ID, createActionGroup(actions), false);
    mainToolbar.setTargetComponent(this);
    var toolBarBox = Box.createHorizontalBox();
    toolBarBox.add(mainToolbar.getComponent());
    super.setToolbar(toolBarBox);
    mainToolbar.getComponent().setVisible(true);
  }

  private static ActionGroup createActionGroup(Collection<AnAction> actions) {
    var actionGroup = new DefaultActionGroup();
    actions.forEach(actionGroup::add);
    return actionGroup;
  }

  private void createIssuesTree() {
    treeBuilder = new IssueTreeModelBuilder(project);
    var model = treeBuilder.createModel(false);
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

  private void createOldIssuesTree() {
    oldTreeBuilder = new IssueTreeModelBuilder(project);
    var model = oldTreeBuilder.createModel(true);
    oldTree = new IssueTree(project, model);
    oldTree.addTreeSelectionListener(e -> oldIssueTreeSelectionChanged());
    oldTree.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_ESCAPE == e.getKeyCode()) {
          var highlighting = SonarLintUtils.getService(project, EditorDecorator.class);
          highlighting.removeHighlights();
        }
      }
    });
    oldTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
  }

  @CheckForNull
  private OccurenceNavigator.OccurenceInfo occurrence(Tree tree, @Nullable IssueNode node) {
    if (node == null) {
      return null;
    }

    var path = new TreePath(node.getPath());
    tree.getSelectionModel().setSelectionPath(path);
    tree.scrollPathToVisible(path);

    var range = node.issue().getRange();
    var startOffset = (range != null) ? range.getStartOffset() : 0;
    return new OccurenceNavigator.OccurenceInfo(
      new OpenFileDescriptor(project, node.issue().file(), startOffset),
      -1,
      -1);
  }

  @Override
  public boolean hasNextOccurence() {
    // relies on the assumption that a TreeNodes will always be the last row in the table view of the tree
    var isOldTree = tree.getSelectionPath() == null;
    var path = isOldTree ? oldTree.getSelectionPath() : tree.getSelectionPath();
    if (path == null) {
      return false;
    }

    return isOldTree ? fetchNextOccurenceInfo(path, oldTree) : fetchNextOccurenceInfo(path, tree);
  }

  private static boolean fetchNextOccurenceInfo(TreePath path, Tree tree) {
    var node = (DefaultMutableTreeNode) path.getLastPathComponent();
    if (node instanceof IssueNode) {
      return tree.getRowCount() != tree.getRowForPath(path) + 1;
    } else {
      return node.getChildCount() > 0;
    }
  }

  @Override
  public boolean hasPreviousOccurence() {
    var path = (tree.getSelectionPath() == null) ? oldTree.getSelectionPath() : null;
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
    var info = fetchNextOccurenceInfo(tree, treeBuilder);
    if (info == null) {
      info = fetchNextOccurenceInfo(oldTree, oldTreeBuilder);
    }

    return info;
  }

  @CheckForNull
  @Override
  public OccurenceNavigator.OccurenceInfo goPreviousOccurence() {
    var info = fetchPreviousOccurenceInfo(tree, treeBuilder);
    if (info == null) {
      info = fetchPreviousOccurenceInfo(oldTree, oldTreeBuilder);
    }

    return info;
  }

  private OccurenceInfo fetchPreviousOccurenceInfo(Tree tree, IssueTreeModelBuilder treeBuilder) {
    var path = tree.getSelectionPath();
    if (path == null) {
      return null;
    }
    return occurrence(tree, treeBuilder.getPreviousIssue((AbstractNode) path.getLastPathComponent()));
  }

  private OccurenceInfo fetchNextOccurenceInfo(Tree tree, IssueTreeModelBuilder treeBuilder) {
    var path = tree.getSelectionPath();
    if (path == null) {
      return null;
    }
    return occurrence(tree, treeBuilder.getNextIssue((AbstractNode) path.getLastPathComponent()));
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
    var issueNode = TreeUtil.findNode(((DefaultMutableTreeNode) tree.getModel().getRoot()), node -> node instanceof IssueNode iNode && iNode.issue().equals(issue));
    if (issueNode == null) {
      SonarLintConsole.get(project).error("Cannot select issue in the tree");
      return;
    }
    tree.setSelectionPath(null);
    tree.addSelectionPath(new TreePath(issueNode.getPath()));
  }

  public void selectLocationsTab() {
    findingDetailsPanel.selectLocationsTab();
  }

  public void selectRulesTab() {
    findingDetailsPanel.selectRulesTab();
  }

  private void clearSelection() {
    findingDetailsPanel.clear();
    var highlighting = SonarLintUtils.getService(project, EditorDecorator.class);
    highlighting.removeHighlights();
  }

  private void issueTreeSelectionChanged(TreeSelectionEvent e) {
    if (!tree.isSelectionEmpty()) {
      oldTree.clearSelection();
    }
    var selectedIssueNodes = tree.getSelectedNodes(IssueNode.class, null);
    if (selectedIssueNodes.length > 0) {
      var issue = selectedIssueNodes[0].issue();
      updateOnSelect(issue);
    } else {
      clearSelection();
    }
  }

  private void oldIssueTreeSelectionChanged(TreeSelectionEvent e) {
    if (!oldTree.isSelectionEmpty()) {
      tree.clearSelection();
    }
    var selectedIssueNodes = oldTree.getSelectedNodes(IssueNode.class, null);
    if (selectedIssueNodes.length > 0) {
      var issue = selectedIssueNodes[0].issue();
      updateOnSelect(issue);
    } else {
      clearSelection();
    }
  }

  private void handleListener() {
    tree.addTreeSelectionListener(this::issueTreeSelectionChanged);
    oldTree.addTreeSelectionListener(this::oldIssueTreeSelectionChanged);
  }

  private void updateOnSelect(LiveFinding liveFinding) {
    findingDetailsPanel.show(liveFinding);
  }

  public <T extends Finding> void updateOnSelect(@Nullable LiveFinding issue, ShowFinding<T> showFinding) {
    if (issue != null) {
      findingDetailsPanel.show(issue);
    } else {
      if (showFinding.getCodeSnippet() == null) {
        SonarLintProjectNotifications.Companion.get(project)
          .notifyUnableToOpenFinding("issue", "The issue could not be detected by SonarLint in the current code.");
        return;
      }
      runOnPooledThread(project, () -> {
        var matcher = new TextRangeMatcher(project);
        var rangeMarker = computeReadActionSafely(project, () -> matcher.matchWithCode(showFinding.getFile(), showFinding.getTextRange(), showFinding.getCodeSnippet()));
        if (rangeMarker == null) {
          SonarLintProjectNotifications.Companion.get(project)
            .notifyUnableToOpenFinding("issue", "The issue could not be detected by SonarLint in the current code.");
          return;
        }

        runOnUiThread(project,
          () -> findingDetailsPanel.showServerOnlyIssue(showFinding.getModule(), showFinding.getFile(), showFinding.getRuleKey(), rangeMarker, showFinding.getFlows(),
            showFinding.getFlowMessage()));
      });
    }
  }

}
