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

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.BorderLayout;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import org.jetbrains.annotations.NonNls;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.messages.IssueStoreListener;
import org.sonarlint.intellij.messages.StatusListener;
import org.sonarlint.intellij.ui.scope.AbstractScope;
import org.sonarlint.intellij.ui.scope.CurrentFileScope;

public class SonarLintIssuesPanel extends AbstractIssuesPanel implements DataProvider {
  private static final String GROUP_ID = "SonarLint.issuestoolwindow";
  private static final String SPLIT_PROPORTION_PROPERTY = "SONARLINT_ISSUES_SPLIT_PROPORTION";
  private static final String FLOWS_SPLIT_PROPORTION_PROPERTY = "SONARLINT_ISSUES_FLOWS_SPLIT_PROPORTION";

  private final IssueManager issueManager;
  private final AbstractScope scope;

  public SonarLintIssuesPanel(Project project, IssueManager issueManager, ProjectBindingManager projectBindingManager) {
    super(project);
    this.issueManager = issueManager;
    this.scope = new CurrentFileScope(project);

    // Issues panel
    JPanel issuesPanel = new JPanel(new BorderLayout());
    issuesPanel.add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);
    issuesPanel.add(new AutoTriggerStatusPanel(project).getPanel(), BorderLayout.SOUTH);

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

    subscribeToEvents();
    updateTree();
  }

  private void subscribeToEvents() {
    scope.addListener(this::updateTree);
    MessageBusConnection busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(IssueStoreListener.SONARLINT_ISSUE_STORE_TOPIC, new IssueStoreListener() {

      @Override public void filesChanged(final Map<VirtualFile, Collection<LiveIssue>> map) {
        ApplicationManager.getApplication().invokeLater(SonarLintIssuesPanel.this::updateTree);
      }

      @Override public void allChanged() {
        ApplicationManager.getApplication().invokeLater(SonarLintIssuesPanel.this::updateTree);
      }
    });

    busConnection.subscribe(StatusListener.SONARLINT_STATUS_TOPIC,
      newStatus -> ApplicationManager.getApplication().invokeLater(mainToolbar::updateActionsImmediately));
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

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (AbstractScope.SCOPE_DATA_KEY.is(dataId)) {
      return scope;
    }

    return null;
  }

  @Override
  protected String getToolbarGroupId() {
    return GROUP_ID;
  }
}
