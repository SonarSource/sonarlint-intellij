/**
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

import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.tree.DefaultTreeModel;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.EditSourceOnEnterKeyHandler;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.sonarlint.intellij.analysis.SonarLintStatus;
import org.sonarlint.intellij.issue.IssuePointer;
import org.sonarlint.intellij.issue.IssueStore;
import org.sonarlint.intellij.messages.AnalysisResultsListener;
import org.sonarlint.intellij.messages.StatusListener;
import org.sonarlint.intellij.ui.scope.CurrentFileScope;
import org.sonarlint.intellij.ui.scope.IssueTreeScope;
import org.sonarlint.intellij.ui.scope.ProjectScope;
import org.sonarlint.intellij.ui.tree.IssueTree;
import org.sonarlint.intellij.ui.tree.IssueTreeCellRenderer;
import org.sonarlint.intellij.ui.tree.TreeModelBuilder;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Map;

public class SonarLintIssuesPanel extends SimpleToolWindowPanel implements DataProvider {
  private static final String ID = "SonarLint";
  private static final String GROUP_ID = "SonarLint.toolwindow";

  private final Project project;
  private final IssueStore issueStore;
  private Tree tree;
  private ActionToolbar mainToolbar;
  private IssueTreeScope scope;
  private TreeModelBuilder treeBuilder;

  public SonarLintIssuesPanel(Project project) {
    super(false, true);
    this.project = project;
    this.issueStore = project.getComponent(IssueStore.class);

    addToolbar();

    JPanel panel = new JPanel(new BorderLayout());
    createTree();
    panel.add(createScopePanel(), BorderLayout.NORTH);
    panel.add(createTreePanel(), BorderLayout.CENTER);

    super.setContent(panel);

    MessageBusConnection busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(AnalysisResultsListener.SONARLINT_ANALYSIS_DONE_TOPIC, new AnalysisResultsListener() {
      @Override public void analysisDone(final Map<VirtualFile, Collection<IssuePointer>> issuesPerFile) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override public void run() {
            treeBuilder.updateFiles(issuesPerFile);
          }
        });
      }
    });
    busConnection.subscribe(StatusListener.SONARLINT_STATUS_TOPIC, new StatusListener() {
      @Override public void changed(SonarLintStatus.Status newStatus) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            // activate/deactivate icons as soon as SonarLint status changes
            mainToolbar.updateActionsImmediately();
          }
        });
      }
    });
    updateTree();
  }

  private void switchScope(IssueTreeScope newScope) {
    if(scope != null) {
      scope.removeListeners();
    }
    scope = newScope;
    scope.addListener(new IssueTreeScope.ScopeListener() {
      @Override public void conditionChanged() {
        updateTree();
      }
    });
  }

  private JComponent createScopePanel() {
    DefaultComboBoxModel comboModel = new DefaultComboBoxModel();
    comboModel.addElement(new CurrentFileScope(project));
    comboModel.addElement(new ProjectScope());

    final ComboBox scopeComboBox = new ComboBox(comboModel);
    scopeComboBox.addActionListener(new ActionListener() {
      @Override public void actionPerformed(ActionEvent e) {
        switchScope((IssueTreeScope) scopeComboBox.getSelectedItem());
        updateTree();
      }
    });
    switchScope((IssueTreeScope) scopeComboBox.getSelectedItem());
    JPanel scopePanel = new JPanel(new GridBagLayout());
    final JLabel scopesLabel = new JLabel("Scope:");
    scopesLabel.setDisplayedMnemonic('S');
    scopesLabel.setLabelFor(scopeComboBox);
    final GridBagConstraints gc =
      new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE,
        new Insets(2, 2, 2, 2), 0, 0);
    scopePanel.add(scopesLabel, gc);
    scopePanel.add(scopeComboBox, gc);

    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1;
    scopePanel.add(Box.createHorizontalBox(), gc);

    return scopePanel;
  }

  private void addToolbar() {
    ActionGroup mainActionGroup = (ActionGroup) ActionManager.getInstance().getAction(GROUP_ID);
    mainToolbar = ActionManager.getInstance().createActionToolbar(ID, mainActionGroup, false);

    Box toolBarBox = Box.createHorizontalBox();
    toolBarBox.add(mainToolbar.getComponent());

    super.setToolbar(toolBarBox);
    mainToolbar.getComponent().setVisible(true);
  }

  public void updateTree() {
    treeBuilder.updateModel(issueStore.getAll(), scope.getCondition());
    tree.expandRow(0);
    if(tree.getRowCount() > 1) {
      tree.expandRow(1);
    }
  }

  private JComponent createTreePanel() {
    return ScrollPaneFactory.createScrollPane(tree);
  }

  private void createTree() {
    treeBuilder = new TreeModelBuilder();
    DefaultTreeModel model = treeBuilder.createModel();
    tree = new IssueTree(project, model);
    UIUtil.setLineStyleAngled(tree);
    tree.setShowsRootHandles(true);
    tree.setRootVisible(true);
    tree.setCellRenderer(new IssueTreeCellRenderer());
    tree.expandRow(0);

    DefaultActionGroup group = new DefaultActionGroup();
    group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE));
    group.addSeparator();
    group.add(ActionManager.getInstance().getAction(IdeActions.GROUP_VERSION_CONTROLS));
    PopupHandler.installPopupHandler(tree, group, ActionPlaces.TODO_VIEW_POPUP, ActionManager.getInstance());

    EditSourceOnDoubleClickHandler.install(tree);
    EditSourceOnEnterKeyHandler.install(tree);
  }
}
