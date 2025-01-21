/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import javax.swing.JScrollPane;
import org.jetbrains.annotations.NonNls;
import org.sonarlint.intellij.SonarLintIcons;
import org.sonarlint.intellij.analysis.AnalysisReadinessCache;
import org.sonarlint.intellij.cayc.CleanAsYouCodeService;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.finding.Finding;
import org.sonarlint.intellij.finding.LiveFinding;
import org.sonarlint.intellij.finding.ShowFinding;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.messages.StatusListener;
import org.sonarlint.intellij.ui.tree.IssueTreeModelBuilder;
import org.sonarlint.intellij.util.SonarLintActions;

import static java.util.function.Predicate.not;
import static org.sonarlint.intellij.actions.RestartBackendAction.SONARLINT_ERROR_MSG;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.ui.SonarLintToolWindowFactory.createSplitter;
import static org.sonarlint.intellij.ui.UiUtils.runOnUiThread;

public class CurrentFilePanel extends AbstractIssuesPanel {

  public static final String SONARLINT_TOOLWINDOW_ID = "SonarQube for IDE";
  private static final String SPLIT_PROPORTION_PROPERTY = "SONARLINT_ISSUES_SPLIT_PROPORTION";
  private final JBPanelWithEmptyText issuesPanel;
  private final JScrollPane treeScrollPane;
  private final AnAction analyzeCurrentFileAction = SonarLintActions.getInstance().analyzeCurrentFileAction();
  private final AnAction restartSonarLintAction = SonarLintActions.getInstance().restartSonarLintAction();
  private VirtualFile currentFile;
  private Collection<LiveIssue> currentIssues;

  public CurrentFilePanel(Project project) {
    super(project);

    // Issues panel
    setToolbar(actions());

    var treePanel = new JBPanel<CurrentFilePanel>(new VerticalFlowLayout(0, 0));
    treePanel.add(tree);
    treePanel.add(oldTree);

    treeScrollPane = ScrollPaneFactory.createScrollPane(treePanel, true);

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
    oldTreeBuilder.allowResolvedIssues(allowResolved);
    refreshModel();
  }

  @Override
  public void dispose() {
    // Nothing to do
  }

  private static Collection<AnAction> actions() {
    return List.of(
      ActionManager.getInstance().getAction("SonarLint.SetFocusNewCode"),
      SonarLintActions.getInstance().analyzeCurrentFileAction(),
      ActionManager.getInstance().getAction("SonarLint.toolwindow.Cancel"),
      SonarLintActions.getInstance().includeResolvedIssuesAction(),
      ActionManager.getInstance().getAction("SonarLint.toolwindow.Configure"),
      SonarLintActions.getInstance().clearIssues());
  }

  public void update(@Nullable VirtualFile file, @Nullable Collection<LiveIssue> issues) {
    currentFile = null;
    currentIssues = null;
    var statusText = issuesPanel.getEmptyText();
    var backendIsAlive = getService(BackendService.class).isAlive();
    if (!backendIsAlive) {
      statusText.setText(SONARLINT_ERROR_MSG);
      statusText.appendLine("Restart SonarQube for IDE Service", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
        ignore -> ActionUtil.invokeAction(restartSonarLintAction, this, CurrentFilePanel.SONARLINT_TOOLWINDOW_ID, null, null));
      disableEmptyDisplay(false);
      populateSubTree(tree, treeBuilder, Map.of());
      populateSubTree(oldTree, oldTreeBuilder, Map.of());
      return;
    }
    if (file == null) {
      statusText.setText("No file opened in the editor");
      disableEmptyDisplay(false);
      populateSubTree(tree, treeBuilder, Map.of());
      populateSubTree(oldTree, oldTreeBuilder, Map.of());
      return;
    }
    if (issues == null) {
      var templateText = analyzeCurrentFileAction.getTemplateText();

      if (getService(project, AnalysisReadinessCache.class).isReady()) {
        statusText.setText("No analysis done on the current opened file");
        if (templateText != null) {
          statusText.appendLine(templateText, SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
            ignore -> ActionUtil.invokeAction(analyzeCurrentFileAction, this, CurrentFilePanel.SONARLINT_TOOLWINDOW_ID, null, null));
        }
      } else {
        statusText.setText("Waiting for SonarQube for IDE to be ready");
      }

      issues = Collections.emptyList();
    } else {
      statusText.setText("No issues to display");
    }
    disableEmptyDisplay(!issues.isEmpty());

    this.currentFile = file;
    this.currentIssues = List.copyOf(issues);
    if (getService(CleanAsYouCodeService.class).shouldFocusOnNewCode(project)) {
      var oldIssues = this.currentIssues.stream().filter(not(LiveFinding::isOnNewCode)).toList();
      var newIssues = this.currentIssues.stream().filter(LiveFinding::isOnNewCode).toList();
      populateSubTree(tree, treeBuilder, Map.of(file, newIssues));
      populateSubTree(oldTree, oldTreeBuilder, Map.of(file, oldIssues));
      oldTree.setVisible(true);
      updateIcon(file, newIssues);
    } else {
      populateSubTree(tree, treeBuilder, Map.of(file, this.currentIssues));
      populateSubTree(oldTree, oldTreeBuilder, Collections.emptyMap());
      oldTree.setVisible(false);
      updateIcon(file, this.currentIssues);
    }
    expandTree();
  }

  private static void populateSubTree(Tree tree, IssueTreeModelBuilder treeBuilder, Map<VirtualFile, Collection<LiveIssue>> issues) {
    treeBuilder.updateModel(issues);
    tree.setShowsRootHandles(!issues.isEmpty());
  }

  @CheckForNull
  public LiveIssue doesIssueExistFiltered(String issueKey) {
    var issue = treeBuilder.findIssueByKey(issueKey);
    if (issue.isEmpty()) {
      issue = oldTreeBuilder.findIssueByKey(issueKey);
    }
    return issue.orElse(null);
  }

  public boolean doesIssueExist(String issueKey) {
    return treeBuilder.doesIssueExists(issueKey) || oldTreeBuilder.doesIssueExists(issueKey);
  }

  public <T extends Finding> void trySelectFilteredIssue(@Nullable LiveIssue issue, ShowFinding<T> showFinding) {
    updateOnSelect(issue, showFinding);
  }

  private void updateIcon(@Nullable VirtualFile file, Collection<LiveIssue> issues) {
    var toolWindow = ToolWindowManager.getInstance(project).getToolWindow(SONARLINT_TOOLWINDOW_ID);
    if (toolWindow != null) {
      var isEmpty = issues.stream().filter(i -> !i.isResolved()).collect(Collectors.toSet()).isEmpty();
      doUpdateIcon(file, isEmpty, toolWindow);
    }
  }

  private static void doUpdateIcon(@Nullable VirtualFile file, boolean isEmpty, ToolWindow toolWindow) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    boolean empty = file == null || isEmpty;
    toolWindow.setIcon(empty ? SonarLintIcons.SONARQUBE_FOR_INTELLIJ_EMPTY_TOOLWINDOW : SonarLintIcons.SONARQUBE_FOR_INTELLIJ_TOOLWINDOW);
  }

  private void expandTree() {
    TreeUtil.expandAll(tree);
  }

  private void disableEmptyDisplay(boolean state) {
    treeScrollPane.setVisible(state);
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
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
    runOnUiThread(project, this::expandTree);
  }

  public void refreshView() {
    runOnUiThread(project, () -> update(currentFile, currentIssues));
  }
}
