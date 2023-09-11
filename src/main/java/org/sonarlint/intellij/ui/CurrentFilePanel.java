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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.swing.JScrollPane;
import org.jetbrains.annotations.NonNls;
import org.sonarlint.intellij.SonarLintIcons;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.messages.StatusListener;
import org.sonarlint.intellij.util.SonarLintActions;

import static org.sonarlint.intellij.ui.SonarLintToolWindowFactory.createSplitter;
import static org.sonarlint.intellij.ui.UiUtils.runOnUiThread;

public class CurrentFilePanel extends AbstractIssuesPanel {

  public static final String SONARLINT_TOOLWINDOW_ID = "SonarLint";
  private static final String SPLIT_PROPORTION_PROPERTY = "SONARLINT_ISSUES_SPLIT_PROPORTION";
  private final JBPanelWithEmptyText issuesPanel;
  private final JScrollPane treeScrollPane;
  private final AnAction analyzeCurrentFileAction = SonarLintActions.getInstance().analyzeCurrentFileAction();

  public CurrentFilePanel(Project project) {
    super(project);

    // Issues panel
    setToolbar(actions());

    var treePanel = new JBPanel<CurrentFilePanel>(new VerticalFlowLayout(0, 0));
    treePanel.add(tree);
    treePanel.add(oldTree);

    treeScrollPane = ScrollPaneFactory.createScrollPane(treePanel);

    issuesPanel = new JBPanelWithEmptyText(new BorderLayout());
    var statusText = issuesPanel.getEmptyText();
    statusText.setText("No analysis done");
    issuesPanel.add(treeScrollPane, BorderLayout.CENTER);
    disableEmptyDisplay(false);

    var mainPanel = new JBPanel<CurrentFilePanel>(new BorderLayout());
    mainPanel.add(issuesPanel);
    mainPanel.add(new CurrentFileStatusPanel(project), BorderLayout.SOUTH);

    findingDetailsPanel.setMinimumSize(new Dimension(350, 200));
    var splitter = createSplitter(project, this, this, mainPanel, findingDetailsPanel, SPLIT_PROPORTION_PROPERTY, 0.5f);

    super.setContent(splitter);
    project.getMessageBus().connect().subscribe(StatusListener.SONARLINT_STATUS_TOPIC,
      newStatus -> runOnUiThread(project, this::refreshToolbar));
  }

  public void allowResolvedIssues(boolean allowResolved) {
    treeBuilder.allowResolvedIssues(allowResolved);
    refreshModel();
  }

  @Override
  public void dispose() {
    // Nothing to do
  }

  private static Collection<AnAction> actions() {
    return List.of(
      SonarLintActions.getInstance().analyzeCurrentFileAction(),
      ActionManager.getInstance().getAction("SonarLint.toolwindow.Cancel"),
      SonarLintActions.getInstance().includeResolvedIssuesAction(),
      ActionManager.getInstance().getAction("SonarLint.toolwindow.Configure"),
      SonarLintActions.getInstance().clearIssues());
  }

  public void update(@Nullable VirtualFile file, @Nullable Collection<LiveIssue> issues) {
    var statusText = issuesPanel.getEmptyText();
    String emptyText;
    Collection<LiveIssue> liveIssues = issues;
    if (file != null) {
      emptyText = liveIssues == null ? "No analysis done on the current opened file" : "No issues found in the current opened file";
      statusText.setText(emptyText);
      if (liveIssues == null && (analyzeCurrentFileAction.getTemplateText() != null)) {
        statusText.appendLine(analyzeCurrentFileAction.getTemplateText(), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
          ignore -> ActionUtil.invokeAction(analyzeCurrentFileAction, this, CurrentFilePanel.SONARLINT_TOOLWINDOW_ID, null, null));
      }
    } else {
      emptyText = "No file opened in the editor";
      statusText.setText(emptyText);
    }

    if (liveIssues == null) {
      liveIssues = Collections.emptyList();
    }
    update(file, List.copyOf(liveIssues), emptyText);
  }

  private void update(@Nullable VirtualFile file, Collection<LiveIssue> issues, String emptyText) {
    if (file == null) {
      disableEmptyDisplay(false);
      treeBuilder.updateModel(Map.of(), emptyText);
      oldTreeBuilder.updateModel(Map.of(), emptyText);
    } else {
      disableEmptyDisplay(!issues.isEmpty());
      treeBuilder.updateModel(Map.of(file, issues), emptyText);
      oldTreeBuilder.updateModel(Map.of(file, issues), emptyText);
    }
    expandTree();
    updateIcon(file, issues);
  }

  private void updateIcon(@Nullable VirtualFile file, Collection<LiveIssue> issues) {
    var toolWindow = ToolWindowManager.getInstance(project).getToolWindow(SONARLINT_TOOLWINDOW_ID);
    if (toolWindow != null) {
      doUpdateIcon(file, issues, toolWindow);
    }
  }

  private static void doUpdateIcon(@Nullable VirtualFile file, Collection<LiveIssue> issues, ToolWindow toolWindow) {
    boolean empty = file == null || issues.isEmpty();
    toolWindow.setIcon(empty ? SonarLintIcons.SONARLINT_TOOLWINDOW_EMPTY : SonarLintIcons.SONARLINT_TOOLWINDOW);
  }

  private void expandTree() {
    TreeUtil.expandAll(tree);
  }

  private void disableEmptyDisplay(boolean state) {
    tree.setShowsRootHandles(true);
    oldTree.setShowsRootHandles(true);
    treeScrollPane.setVisible(state);
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    // workaround for https://youtrack.jetbrains.com/issue/IDEA-262818
    // remove if fixed before the official 2021.1
    if (!EventQueue.isDispatchThread()) {
      return null;
    }
    if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      return SonarLintUtils.getSelectedFile(project);
    }

    return null;
  }

  public void remove(LiveIssue issue) {
    treeBuilder.remove(issue);
    oldTreeBuilder.remove(issue);
  }

  public void refreshModel() {
    treeBuilder.refreshModel(project);
    oldTreeBuilder.refreshModel(project);
    expandTree();
  }
}
