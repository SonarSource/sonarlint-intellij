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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.tree.TreeUtil;
import icons.SonarLintIcons;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.swing.JPanel;
import org.jetbrains.annotations.NonNls;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.messages.StatusListener;
import org.sonarlint.intellij.util.SonarLintActions;

import static org.sonarlint.intellij.ui.SonarLintToolWindowFactory.createSplitter;

public class CurrentFilePanel extends AbstractIssuesPanel implements Disposable {
  private static final String SPLIT_PROPORTION_PROPERTY = "SONARLINT_ISSUES_SPLIT_PROPORTION";
  public static final String SONARLINT_TOOLWINDOW_ID = "SonarLint";

  public CurrentFilePanel(Project project) {
    super(project);

    // Issues panel
    setToolbar(actions());
    var issuesPanel = new JPanel(new BorderLayout());
    issuesPanel.add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER);
    issuesPanel.add(new CurrentFileStatusPanel(project).getPanel(), BorderLayout.SOUTH);

    var splitter = createSplitter(project, this, this, issuesPanel, detailsTab, SPLIT_PROPORTION_PROPERTY, 0.5f);
    super.setContent(splitter);
    project.getMessageBus().connect().subscribe(StatusListener.SONARLINT_STATUS_TOPIC,
      newStatus -> ApplicationManager.getApplication().invokeLater(this::refreshToolbar));
  }

  @Override
  public void dispose() {
    // Nothing to do
  }

  private static Collection<AnAction> actions() {
    return List.of(
      ActionManager.getInstance().getAction("SonarLint.AnalyzeFiles"),
      ActionManager.getInstance().getAction("SonarLint.toolwindow.Cancel"),
      ActionManager.getInstance().getAction("SonarLint.toolwindow.Configure"),
      SonarLintActions.getInstance().clearIssues()
    );
  }

  public void update(@Nullable VirtualFile file, @Nullable Collection<LiveIssue> issues) {
    String emptyText;
    Collection<LiveIssue> liveIssues = issues;
    if (file != null) {
      emptyText = liveIssues == null ? "No analysis done on the current opened file" : "No issues found in the current opened file";
    } else {
      emptyText = "No file opened in the editor";
    }
    if (liveIssues == null) {
      liveIssues = Collections.emptyList();
    }
    update(file, List.copyOf(liveIssues), emptyText);
  }

  private void update(@Nullable VirtualFile file, Collection<LiveIssue> issues, String emptyText) {
    if (file == null) {
      treeBuilder.updateModel(Map.of(), emptyText);
    } else {
      treeBuilder.updateModel(Map.of(file, issues), emptyText);
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
}
