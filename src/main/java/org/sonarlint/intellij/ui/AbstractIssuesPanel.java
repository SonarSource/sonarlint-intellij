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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Collection;
import javax.annotation.Nullable;
import javax.swing.Box;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.editor.EditorDecorator;
import org.sonarlint.intellij.finding.Finding;
import org.sonarlint.intellij.finding.LiveFinding;
import org.sonarlint.intellij.finding.ShowFinding;
import org.sonarlint.intellij.finding.TextRangeMatcher;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications;
import org.sonarlint.intellij.ui.nodes.IssueNode;
import org.sonarlint.intellij.ui.tree.IssueTree;
import org.sonarlint.intellij.ui.tree.IssueTreeModelBuilder;

import static org.sonarlint.intellij.common.ui.ReadActionUtils.computeReadActionSafely;
import static org.sonarlint.intellij.ui.UiUtils.runOnUiThread;
import static org.sonarlint.intellij.util.ThreadUtilsKt.runOnPooledThread;

public abstract class AbstractIssuesPanel extends SimpleToolWindowPanel implements Disposable {
  private static final String ID = "SonarQube for IntelliJ";
  protected final Project project;
  protected Tree tree;
  protected Tree oldTree;
  protected IssueTreeModelBuilder treeBuilder;
  protected IssueTreeModelBuilder oldTreeBuilder;
  private ActionToolbar mainToolbar;
  protected FindingDetailsPanel findingDetailsPanel;

  protected AbstractIssuesPanel(Project project) {
    super(false, false);
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

  public void setSelectedIssue(LiveIssue issue) {
    var issueNode = TreeUtil.findNode(((DefaultMutableTreeNode) tree.getModel().getRoot()), node -> node instanceof IssueNode iNode && iNode.issue().equals(issue));
    if (issueNode == null) {
      issueNode = TreeUtil.findNode(((DefaultMutableTreeNode) oldTree.getModel().getRoot()), node -> node instanceof IssueNode iNode && iNode.issue().equals(issue));
      if (issueNode == null) {
        SonarLintConsole.get(project).error("Cannot select issue in the tree");
      } else {
        oldTree.setSelectionPath(null);
        oldTree.addSelectionPath(new TreePath(issueNode.getPath()));
      }
    } else {
      tree.setSelectionPath(null);
      tree.addSelectionPath(new TreePath(issueNode.getPath()));
    }
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
      updateOnSelect(selectedIssueNodes[0].issue());
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
      updateOnSelect(selectedIssueNodes[0].issue());
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
      updateOnSelect(issue);
    } else {
      if (showFinding.getCodeSnippet() == null) {
        SonarLintProjectNotifications.Companion.get(project)
          .notifyUnableToOpenFinding("The issue could not be detected by SonarQube for IntelliJ in the current code");
        return;
      }
      runOnPooledThread(project, () -> {
        var matcher = new TextRangeMatcher(project);
        var rangeMarker = computeReadActionSafely(project, () -> matcher.matchWithCode(showFinding.getFile(), showFinding.getTextRange(), showFinding.getCodeSnippet()));
        if (rangeMarker == null) {
          SonarLintProjectNotifications.Companion.get(project)
            .notifyUnableToOpenFinding("The issue could not be detected by SonarQube for IntelliJ in the current code");
          return;
        }

        runOnUiThread(project,
          () -> findingDetailsPanel.showServerOnlyIssue(showFinding.getModule(), showFinding.getFile(), showFinding.getRuleKey(), rangeMarker, showFinding.getFlows(),
            showFinding.getFlowMessage()));
      });
    }
  }

}
