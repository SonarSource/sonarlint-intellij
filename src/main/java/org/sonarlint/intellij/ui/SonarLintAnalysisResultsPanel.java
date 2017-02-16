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
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.messages.StatusListener;
import org.sonarlint.intellij.ui.scope.AbstractScope;
import org.sonarlint.intellij.ui.scope.AllFilesScope;
import org.sonarlint.intellij.ui.scope.ChangedFilesScope;

public class SonarLintAnalysisResultsPanel extends AbstractIssuesPanel implements OccurenceNavigator, AbstractScope.ScopeListener, DataProvider {
  private static final String SELECTED_SCOPE_KEY = "SONARLINT_ANALYSIS_RESULTS_SCOPE";
  private static final String SPLIT_PROPORTION_PROPERTY = "SONARLINT_ANALYSIS_RESULTS_SPLIT_PROPORTION";

  private final transient LastAnalysisPanel lastAnalysisPanel;
  private transient AbstractScope scope;

  private transient ChangedFilesScope changedFilesScope;
  private ComboBox scopeComboBox;

  public SonarLintAnalysisResultsPanel(Project project, ProjectBindingManager projectBindingManager) {
    super(project, projectBindingManager);
    this.lastAnalysisPanel = new LastAnalysisPanel(project);

    // Issues panel with tree
    JPanel issuesPanel = new JPanel(new BorderLayout());
    issuesPanel.add(createScopePanel(), BorderLayout.NORTH);
    issuesPanel.add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);
    issuesPanel.add(lastAnalysisPanel.getPanel(), BorderLayout.SOUTH);
    setToolbar(scope.toolbarId());

    // Put everything together
    super.setContent(createSplitter(issuesPanel, detailsTab, SPLIT_PROPORTION_PROPERTY, false, 0.65f));

    // Subscribe to events
    subscribeToEvents();
  }

  private void subscribeToEvents() {
    MessageBusConnection busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(StatusListener.SONARLINT_STATUS_TOPIC, newStatus -> ApplicationManager.getApplication().invokeLater(this::refreshToolbar));
  }

  public void selectChangedFilesScope() {
    scopeComboBox.setSelectedItem(changedFilesScope);
  }

  @Override
  public void updateTexts() {
    lastAnalysisPanel.update(scope.getLastAnalysisDate(), scope.getLabelText());
    treeBuilder.updateEmptyText(scope.getEmptyText());
  }

  @Override
  public void updateIssues() {
    lastAnalysisPanel.update(scope.getLastAnalysisDate(), scope.getLabelText());
    treeBuilder.updateModel(scope.issues(), scope.getEmptyText());
    expandTree();
  }

  private void expandTree() {
    if (treeBuilder.numberIssues() < 30) {
      TreeUtil.expandAll(tree);
    } else {
      tree.expandRow(0);
    }
  }

  private JComponent createScopePanel() {
    DefaultComboBoxModel comboModel = new DefaultComboBoxModel();
    changedFilesScope = new ChangedFilesScope(project);
    comboModel.addElement(changedFilesScope);
    comboModel.addElement(new AllFilesScope(project));

    // set selected element that was last saved, if any
    String savedSelectedScope = PropertiesComponent.getInstance(project).getValue(SELECTED_SCOPE_KEY);
    if (savedSelectedScope != null) {
      for (int i = 0; i < comboModel.getSize(); i++) {
        Object el = comboModel.getElementAt(i);
        if (el.toString().equals(savedSelectedScope)) {
          comboModel.setSelectedItem(el);
          break;
        }
      }
    }

    scopeComboBox = new ComboBox(comboModel);
    scopeComboBox.addActionListener(evt -> switchScope((AbstractScope) scopeComboBox.getSelectedItem()));
    switchScope((AbstractScope) scopeComboBox.getSelectedItem());
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

  private void switchScope(AbstractScope newScope) {
    if (scope != null) {
      scope.removeListeners();
    }
    scope = newScope;
    scope.addListener(this);
    updateIssues();
    updateTexts();
    setToolbar(scope.toolbarId());
    PropertiesComponent.getInstance(project).setValue(SELECTED_SCOPE_KEY, newScope.toString());
    this.refreshToolbar();
  }
}
