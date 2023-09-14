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
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.tools.SimpleActionGroup;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.Box;
import javax.swing.JScrollPane;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.TreeSelectionModel;
import org.sonarlint.intellij.analysis.AnalysisResult;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.editor.EditorDecorator;
import org.sonarlint.intellij.finding.LiveFinding;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.messages.StatusListener;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.ui.nodes.IssueNode;
import org.sonarlint.intellij.ui.nodes.LiveSecurityHotspotNode;
import org.sonarlint.intellij.ui.tree.IssueTree;
import org.sonarlint.intellij.ui.tree.IssueTreeModelBuilder;
import org.sonarlint.intellij.ui.tree.SecurityHotspotTree;
import org.sonarlint.intellij.ui.tree.SecurityHotspotTreeModelBuilder;
import org.sonarlint.intellij.util.SonarLintActions;
import org.sonarlint.intellij.util.SummaryNodeType;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotStatus;

import static org.sonarlint.intellij.ui.SonarLintToolWindowFactory.createSplitter;
import static org.sonarlint.intellij.ui.UiUtils.runOnUiThread;

public class ReportPanel extends SimpleToolWindowPanel implements Disposable {
  private static final String SPLIT_PROPORTION_PROPERTY = "SONARLINT_ANALYSIS_RESULTS_SPLIT_PROPORTION";
  private static final String ID = "SonarLint";
  protected final Project project;
  private final LastAnalysisPanel lastAnalysisPanel;
  protected Tree tree;
  protected Tree oldTree;
  protected IssueTreeModelBuilder treeBuilder;
  protected IssueTreeModelBuilder oldTreeBuilder;
  private ActionToolbar mainToolbar;
  protected SecurityHotspotTreeModelBuilder securityHotspotTreeBuilder;
  protected SecurityHotspotTreeModelBuilder oldSecurityHotspotTreeBuilder;
  protected Tree securityHotspotTree;
  protected Tree oldSecurityHotspotTree;
  private JScrollPane findingsTreePane;
  private FindingDetailsPanel findingDetailsPanel;

  public ReportPanel(Project project) {
    super(false, true);
    this.project = project;
    this.lastAnalysisPanel = new LastAnalysisPanel();

    createIssuesTree();
    createOldIssuesTree();
    createSecurityHotspotsTree();
    createOldSecurityHotspotsTree();
    createFindingDetailsPanel();
    handleListener();
    disableSecurityHotspotTree();

    initPanel();

    // Subscribe to events
    subscribeToEvents();
  }

  public void updateFindings(AnalysisResult analysisResult) {
    if (project.isDisposed()) {
      return;
    }
    lastAnalysisPanel.update(analysisResult.getAnalysisDate(), whatAnalyzed(analysisResult));
    var findings = analysisResult.getFindings();
    treeBuilder.updateModel(findings.getIssuesPerFile(), "No issues found");
    oldTreeBuilder.updateModel(findings.getIssuesPerFile(), "No issues found");
    securityHotspotTreeBuilder.updateModel(findings.getSecurityHotspotsPerFile(), "No Security Hotspots found");
    oldSecurityHotspotTreeBuilder.updateModel(findings.getSecurityHotspotsPerFile(), "No Security Hotspots found");

    disableEmptyDisplay(true);

    if (findings.getSecurityHotspotsPerFile().isEmpty()) {
      disableSecurityHotspotTree();
    } else {
      enableHotspotTree();
    }

    expandTree();
  }

  public void remove(LiveIssue issue) {
    treeBuilder.remove(issue);
  }

  public void updateStatusForSecurityHotspot(String securityHotspotKey, HotspotStatus status) {
    updateStatus(securityHotspotKey, status, securityHotspotTreeBuilder);
  }

  public void updateStatusForOldSecurityHotspot(String securityHotspotKey, HotspotStatus status) {
    updateStatus(securityHotspotKey, status, oldSecurityHotspotTreeBuilder);
  }

  private void updateStatus(String securityHotspotKey, HotspotStatus status, SecurityHotspotTreeModelBuilder builder) {
    var wasUpdated = builder.updateStatusForHotspotWithFileNode(securityHotspotKey, status);
    if (wasUpdated) {
      expandTree();
    }
  }

  private void initPanel() {
    // Findings panel with tree
    var findingsPanel = new JBPanelWithEmptyText(new BorderLayout());

    initEmptyViews(findingsPanel);

    var treePanel = new JBPanel<ReportPanel>(new VerticalFlowLayout(0, 0));
    treePanel.add(tree);
    treePanel.add(securityHotspotTree);
    treePanel.add(oldTree);
    treePanel.add(oldSecurityHotspotTree);
    findingsTreePane = ScrollPaneFactory.createScrollPane(treePanel);
    findingsPanel.add(findingsTreePane, BorderLayout.CENTER);
    findingsPanel.add(lastAnalysisPanel, BorderLayout.SOUTH);
    setToolbar(createActionGroup());
    disableEmptyDisplay(false);

    findingDetailsPanel.setMinimumSize(new Dimension(350, 200));
    // Put everything together
    super.setContent(createSplitter(project, this, this, findingsPanel, findingDetailsPanel, SPLIT_PROPORTION_PROPERTY, 0.5f));
  }

  private void initEmptyViews(JBPanelWithEmptyText findingsPanel) {
    var statusText = findingsPanel.getEmptyText();
    var sonarLintActions = SonarLintActions.getInstance();
    var analyzeChangedFiles = sonarLintActions.analyzeChangedFiles();
    var analyzeAllFiles = sonarLintActions.analyzeAllFiles();
    statusText.setText(LastAnalysisPanel.NEVER_ANALYZED_EMPTY_TEXT);
    statusText.appendLine("");
    if (analyzeChangedFiles.getTemplateText() != null) {
      statusText.appendText(analyzeChangedFiles.getTemplateText(), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
        ignore -> ActionUtil.invokeAction(analyzeChangedFiles, this, CurrentFilePanel.SONARLINT_TOOLWINDOW_ID, null, null));
      if (analyzeAllFiles.getTemplateText() != null) {
        statusText.appendText(" or ");
      }
    }
    if (analyzeAllFiles.getTemplateText() != null) {
      statusText.appendText(analyzeAllFiles.getTemplateText(), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
        ignore -> ActionUtil.invokeAction(analyzeAllFiles, this, CurrentFilePanel.SONARLINT_TOOLWINDOW_ID, null, null));
    }
  }

  private void refreshToolbar() {
    mainToolbar.updateActionsImmediately();
  }

  private void enableHotspotTree() {
    tree.setShowsRootHandles(true);
    oldTree.setShowsRootHandles(true);
    securityHotspotTree.setShowsRootHandles(true);
    oldSecurityHotspotTree.setShowsRootHandles(true);
    securityHotspotTree.setVisible(true);
    oldSecurityHotspotTree.setVisible(true);
  }

  private void disableSecurityHotspotTree() {
    tree.setShowsRootHandles(false);
    oldTree.setShowsRootHandles(false);
    securityHotspotTree.setShowsRootHandles(false);
    securityHotspotTree.setVisible(false);
    oldSecurityHotspotTree.setShowsRootHandles(false);
    oldSecurityHotspotTree.setVisible(false);
  }

  private void issueTreeSelectionChanged(TreeSelectionEvent e) {
    if (!tree.isSelectionEmpty()) {
      securityHotspotTree.clearSelection();
      oldTree.clearSelection();
      oldSecurityHotspotTree.clearSelection();
    }
    applyIssueChanges(tree);
  }

  private void oldIssueTreeSelectionChanged(TreeSelectionEvent e) {
    if (!oldTree.isSelectionEmpty()) {
      tree.clearSelection();
      securityHotspotTree.clearSelection();
      oldSecurityHotspotTree.clearSelection();
    }
    applyIssueChanges(oldTree);
  }

  private void applyIssueChanges(Tree tree) {
    var selectedIssueNodes = tree.getSelectedNodes(IssueNode.class, null);
    if (selectedIssueNodes.length > 0) {
      updateOnSelect(selectedIssueNodes[0].issue());
    } else {
      clearSelection();
    }
  }

  private void securityHotspotTreeSelectionChanged(TreeSelectionEvent e) {
    if (e.getSource() instanceof SecurityHotspotTree) {
      if (!securityHotspotTree.isSelectionEmpty()) {
        tree.clearSelection();
        oldTree.clearSelection();
        oldSecurityHotspotTree.clearSelection();
      }

      applyHotspotChanges(securityHotspotTree);
    }
  }

  private void oldSecurityHotspotTreeSelectionChanged(TreeSelectionEvent e) {
    if (e.getSource() instanceof SecurityHotspotTree) {
      if (!oldSecurityHotspotTree.isSelectionEmpty()) {
        tree.clearSelection();
        oldTree.clearSelection();
        securityHotspotTree.clearSelection();
      }
      applyHotspotChanges(oldSecurityHotspotTree);
    }
  }

  private void applyHotspotChanges(Tree tree) {
    var selectedHotspotsNodes = tree.getSelectedNodes(LiveSecurityHotspotNode.class, null);
    if (selectedHotspotsNodes.length > 0) {
      updateOnSelect(selectedHotspotsNodes[0].getHotspot());
    } else {
      clearSelection();
    }
  }

  private void updateOnSelect(LiveFinding liveFinding) {
    findingDetailsPanel.show(liveFinding);
  }

  private void setToolbar(ActionGroup group) {
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

  private static SimpleActionGroup createActionGroup() {
    var sonarLintActions = SonarLintActions.getInstance();
    var actionGroup = new SimpleActionGroup();
    actionGroup.add(ActionManager.getInstance().getAction("SonarLint.SetFocusNewCode"));
    actionGroup.add(sonarLintActions.analyzeChangedFiles());
    actionGroup.add(sonarLintActions.analyzeAllFiles());
    actionGroup.add(sonarLintActions.cancelAnalysis());
    actionGroup.add(sonarLintActions.configure());
    actionGroup.add(sonarLintActions.clearReport());
    return actionGroup;
  }

  private String whatAnalyzed(AnalysisResult analysisResult) {
    var trigger = analysisResult.getTriggerType();
    if (TriggerType.ALL.equals(trigger)) {
      return "all project files";
    }
    if (TriggerType.CHANGED_FILES.equals(trigger)) {
      return "SCM changed files";
    }
    var filesCount = analysisResult.getAnalyzedFiles().size();
    if (filesCount == 1) {
      return "1 file";
    }
    return filesCount + " files";
  }

  private void handleListener() {
    tree.addTreeSelectionListener(this::issueTreeSelectionChanged);
    oldTree.addTreeSelectionListener(this::oldIssueTreeSelectionChanged);
    securityHotspotTree.addTreeSelectionListener(this::securityHotspotTreeSelectionChanged);
    oldSecurityHotspotTree.addTreeSelectionListener(this::oldSecurityHotspotTreeSelectionChanged);
  }

  private void createSecurityHotspotsTree() {
    securityHotspotTreeBuilder = new SecurityHotspotTreeModelBuilder();
    var model = securityHotspotTreeBuilder.createModel(SummaryNodeType.NEW_SECURITY_HOTSPOT);
    securityHotspotTree = new SecurityHotspotTree(project, model);
    manageInteraction(securityHotspotTree);
  }

  private void createOldSecurityHotspotsTree() {
    oldSecurityHotspotTreeBuilder = new SecurityHotspotTreeModelBuilder();
    var model = oldSecurityHotspotTreeBuilder.createModel(SummaryNodeType.OLD_SECURITY_HOTSPOT);
    oldSecurityHotspotTree = new SecurityHotspotTree(project, model);
    manageInteraction(oldSecurityHotspotTree);
  }

  private void createFindingDetailsPanel() {
    findingDetailsPanel = new FindingDetailsPanel(project, this, FindingKind.MIX);
  }

  private void clearSelection() {
    findingDetailsPanel.clear();
    var highlighting = SonarLintUtils.getService(project, EditorDecorator.class);
    highlighting.removeHighlights();
  }

  private void createIssuesTree() {
    treeBuilder = new IssueTreeModelBuilder();
    var model = treeBuilder.createModel(SummaryNodeType.NEW_ISSUE);
    tree = new IssueTree(project, model);
    manageInteraction(tree);
  }

  private void createOldIssuesTree() {
    oldTreeBuilder = new IssueTreeModelBuilder();
    var model = oldTreeBuilder.createModel(SummaryNodeType.OLD_ISSUE);
    oldTree = new IssueTree(project, model);
    manageInteraction(oldTree);
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

  private void subscribeToEvents() {
    var busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(StatusListener.SONARLINT_STATUS_TOPIC,
      newStatus -> runOnUiThread(project, this::refreshToolbar));
  }

  public void clear() {
    if (project.isDisposed()) {
      return;
    }
    lastAnalysisPanel.clear();
    treeBuilder.clear();
    oldTreeBuilder.clear();
    securityHotspotTreeBuilder.clear();
    oldSecurityHotspotTreeBuilder.clear();
    disableEmptyDisplay(false);
  }

  private void expandTree() {
    if (treeBuilder.numberIssues() < 30) {
      TreeUtil.expandAll(tree);
    } else {
      tree.expandRow(0);
    }

    if (securityHotspotTreeBuilder.numberHotspots() < 30) {
      TreeUtil.expandAll(securityHotspotTree);
    } else {
      securityHotspotTree.expandRow(0);
    }
  }

  private void disableEmptyDisplay(Boolean state) {
    findingsTreePane.setVisible(state);
    lastAnalysisPanel.setVisible(state);
  }

  @Override
  // called automatically because the panel is one of the content of the tool window
  public void dispose() {
    lastAnalysisPanel.dispose();
  }
}
