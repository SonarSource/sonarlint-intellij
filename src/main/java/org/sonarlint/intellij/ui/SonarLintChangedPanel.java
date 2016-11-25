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
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.BorderLayout;
import javax.swing.Box;
import javax.swing.JComponent;
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

public class SonarLintChangedPanel extends AbstractIssuesPanel implements OccurenceNavigator, DataProvider {
  private static final String ID = "SonarLint";
  private static final String GROUP_ID = "SonarLint.changedtoolwindow";
  private static final String SPLIT_PROPORTION = "SONARLINT_CHANGED_ISSUES_SPLIT_PROPORTION";

  private final SonarLintRulePanel rulePanel;
  private final LastAnalysisPanel lastAnalysisPanel;
  private ActionToolbar mainToolbar;

  public SonarLintChangedPanel(Project project, ChangedFilesIssues changedFileIssues) {
    this.project = project;
    this.lastAnalysisPanel = new LastAnalysisPanel(changedFileIssues, project);

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

    super.setContent(createSplitter(issuesPanel, scrollableRulePanel));
    this.treeBuilder.updateModel(changedFileIssues.issues(), x -> true);
    subscribeToEvents();
  }

  private void subscribeToEvents() {
    MessageBusConnection busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(ChangedFilesIssuesListener.CHANGED_FILES_ISSUES_TOPIC, issues -> ApplicationManager.getApplication().invokeLater(() -> {
      treeBuilder.updateModel(issues, x -> true);
      lastAnalysisPanel.update();
      expandTree();
    }));
    busConnection.subscribe(StatusListener.SONARLINT_STATUS_TOPIC, newStatus ->
      ApplicationManager.getApplication().invokeAndWait(mainToolbar::updateActionsImmediately, ModalityState.current()));
  }

  private JComponent createSplitter(JComponent c1, JComponent c2) {
    float savedProportion = PropertiesComponent.getInstance(project).getFloat(SPLIT_PROPORTION, 0.65f);

    final Splitter splitter = new Splitter(false);
    splitter.setFirstComponent(c1);
    splitter.setSecondComponent(c2);
    splitter.setProportion(savedProportion);
    splitter.setHonorComponentsMinimumSize(true);
    splitter.addPropertyChangeListener(Splitter.PROP_PROPORTION,
      evt -> PropertiesComponent.getInstance(project).setValue(SPLIT_PROPORTION, Float.toString(splitter.getProportion())));

    return splitter;
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
    tree = new IssueTree(project, model, true);
    tree.addTreeSelectionListener(e -> issueTreeSelectionChanged());
  }
}
