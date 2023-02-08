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

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.treeStructure.Tree;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.Box;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.TreeSelectionModel;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.editor.EditorDecorator;
import org.sonarlint.intellij.ui.nodes.IssueNode;
import org.sonarlint.intellij.ui.nodes.LiveSecurityHotspotNode;
import org.sonarlint.intellij.ui.tree.FlowsTree;
import org.sonarlint.intellij.ui.tree.FlowsTreeModelBuilder;
import org.sonarlint.intellij.ui.tree.IssueTree;
import org.sonarlint.intellij.ui.tree.IssueTreeModelBuilder;
import org.sonarlint.intellij.ui.tree.SecurityHotspotTree;
import org.sonarlint.intellij.ui.tree.SecurityHotspotTreeModelBuilder;

public abstract class AbstractFindingsPanel extends SimpleToolWindowPanel {
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
  protected SecurityHotspotTreeModelBuilder shTreeBuilder;
  protected Tree shTree;

  protected AbstractFindingsPanel(Project project) {
    super(false, true);
    this.project = project;

    createFlowsTree();
    createIssuesTree();
    createSecurityHotspotsTree();
    createTabs();
    handleListener();
    disableSecurityHotspotTree();
  }

  public void refreshToolbar() {
    mainToolbar.updateActionsImmediately();
  }

  public void enableHotspotTree() {
    tree.setShowsRootHandles(true);
    shTree.setShowsRootHandles(true);
    shTree.setVisible(true);
  }

  public void disableSecurityHotspotTree() {
    tree.setShowsRootHandles(false);
    shTree.setShowsRootHandles(false);
    shTree.setVisible(false);
  }

  private void handleListener() {
    shTree.addTreeSelectionListener(this::findingTreeSelectionChanged);
    tree.addTreeSelectionListener(this::findingTreeSelectionChanged);
  }

  private void createSecurityHotspotsTree() {
    shTreeBuilder = new SecurityHotspotTreeModelBuilder();
    var model = shTreeBuilder.createModel();
    shTree = new SecurityHotspotTree(project, model);
    manageInteraction(shTree);
  }

  private void createTabs() {
    // Flows panel with tree
    var flowsPanel = ScrollPaneFactory.createScrollPane(flowsTree, true);
    flowsPanel.getVerticalScrollBar().setUnitIncrement(10);

    // Rule panel
    rulePanel = new SonarLintRulePanel(project);

    detailsTab = new JBTabbedPane();
    detailsTab.insertTab("Rule", null, rulePanel, "Details about the rule", RULE_TAB_INDEX);
    detailsTab.insertTab("Locations", null, flowsPanel, "All locations involved in the finding", LOCATIONS_TAB_INDEX);
  }

  protected void findingTreeSelectionChanged(TreeSelectionEvent e) {
    if (e.getSource() instanceof IssueTree) {
      if (!tree.isSelectionEmpty()) {
        shTree.clearSelection();
      }
      var selectedIssueNodes = tree.getSelectedNodes(IssueNode.class, null);
      if (selectedIssueNodes.length > 0) {
        var issue = selectedIssueNodes[0].issue();
        var moduleForFile = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(issue.psiFile().getVirtualFile());
        rulePanel.setRuleKey(moduleForFile, issue.getRuleKey(), null);
        SonarLintUtils.getService(project, EditorDecorator.class).highlightIssue(issue);
        flowsTree.getEmptyText().setText("Selected issue doesn't have flows");
        flowsTreeBuilder.populateForIssue(issue);
        flowsTree.expandAll();
      } else {
        clearSelectionChanged();
      }
    } else if (e.getSource() instanceof SecurityHotspotTree) {
      if (!shTree.isSelectionEmpty()) {
        tree.clearSelection();
      }
      var selectedHotspotsNodes = shTree.getSelectedNodes(LiveSecurityHotspotNode.class, null);
      if (selectedHotspotsNodes.length > 0) {
        var securityHotspot = selectedHotspotsNodes[0].getHotspot();
        var moduleForFile = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(securityHotspot.psiFile().getVirtualFile());
        rulePanel.setRuleKey(moduleForFile, securityHotspot.getRuleKey(), null);
        SonarLintUtils.getService(project, EditorDecorator.class).highlight(securityHotspot);
        flowsTree.getEmptyText().setText("Selected security hotspot doesn't have flows");
        flowsTree.expandAll();
      } else {
        clearSelectionChanged();
      }
    }
  }

  private void clearSelectionChanged() {
    flowsTreeBuilder.clearFlows();
    flowsTree.getEmptyText().setText("No finding selected");
    rulePanel.setRuleKey(null, null, null);
    var highlighting = SonarLintUtils.getService(project, EditorDecorator.class);
    highlighting.removeHighlights();
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

  private void createFlowsTree() {
    flowsTreeBuilder = new FlowsTreeModelBuilder();
    var model = flowsTreeBuilder.createModel();
    flowsTree = new FlowsTree(project, model);
    flowsTreeBuilder.clearFlows();
    flowsTree.getEmptyText().setText("No finding selected");
  }

  private void createIssuesTree() {
    treeBuilder = new IssueTreeModelBuilder();
    var model = treeBuilder.createModel();
    tree = new IssueTree(project, model);
    manageInteraction(tree);
  }

  private void manageInteraction(Tree tree) {
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

}
