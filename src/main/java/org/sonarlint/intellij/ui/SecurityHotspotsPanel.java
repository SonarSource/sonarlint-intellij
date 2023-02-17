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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.tools.SimpleActionGroup;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.components.labels.ActionLink;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.BorderLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.TreeSelectionModel;
import org.sonarlint.intellij.actions.SonarConfigureProject;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.editor.EditorDecorator;
import org.sonarlint.intellij.finding.LiveFinding;
import org.sonarlint.intellij.finding.hotspot.InvalidBinding;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.finding.hotspot.NoBinding;
import org.sonarlint.intellij.finding.hotspot.SecurityHotspotsStatus;
import org.sonarlint.intellij.finding.hotspot.ValidStatus;
import org.sonarlint.intellij.ui.nodes.LiveSecurityHotspotNode;
import org.sonarlint.intellij.ui.tree.FlowsTree;
import org.sonarlint.intellij.ui.tree.FlowsTreeModelBuilder;
import org.sonarlint.intellij.ui.tree.SecurityHotspotTree;
import org.sonarlint.intellij.ui.tree.SecurityHotspotTreeModelBuilder;
import org.sonarlint.intellij.util.SonarLintActions;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.ui.SonarLintToolWindowFactory.createSplitter;

public class SecurityHotspotsPanel extends SimpleToolWindowPanel implements Disposable {
  private static final int RULE_TAB_INDEX = 0;
  private static final int LOCATIONS_TAB_INDEX = 1;
  private static final String NO_BINDING_CARD_ID = "NO_BINDING_CARD";
  private static final String INVALID_BINDING_CARD_ID = "INVALID_BINDING_CARD";
  private static final String NO_SECURITY_HOTSPOT_CARD_ID = "NO_SECURITY_HOTSPOT_CARD_ID";
  private static final String SECURITY_HOTSPOTS_LIST_CARD_ID = "SECURITY_HOTSPOTS_LIST_CARD_ID";
  private static final String TOOLBAR_GROUP_ID = "SecurityHotspot";
  private static final String SPLIT_PROPORTION_PROPERTY = "SONARLINT_ANALYSIS_RESULTS_SPLIT_PROPORTION";
  protected SecurityHotspotTreeModelBuilder securityHotspotTreeBuilder;
  protected Tree securityHotspotTree;
  protected FlowsTree flowsTree;
  protected FlowsTreeModelBuilder flowsTreeBuilder;
  protected SonarLintRulePanel rulePanel;
  protected JBTabbedPane detailsTab;
  private final JPanel mainPanel;
  private final Project project;
  private final CardPanel cardPanel;
  private ActionToolbar mainToolbar;
  private SecurityHotspotsStatus status;
  private int securityHotspotCount;

  public SecurityHotspotsPanel(Project project) {
    super(false, true);
    this.project = project;
    securityHotspotCount = 0;
    cardPanel = new CardPanel();
    mainPanel = new JPanel(new BorderLayout());

    createFlowsTree();
    createSecurityHotspotsTree();
    createTabs();
    initPanel();

    super.setContent(mainPanel);
  }

  private void initPanel() {
    var treePanel = new JPanel(new VerticalFlowLayout(0, 0));
    treePanel.add(securityHotspotTree);

    var findingsPanel = new JPanel(new BorderLayout());
    findingsPanel.add(createSplitter(project, this, this,
      ScrollPaneFactory.createScrollPane(treePanel), detailsTab, SPLIT_PROPORTION_PROPERTY, 0.5f));

    cardPanel.add(centeredLabel(new JLabel("The project is not bound to SonarQube 9.7+"), new ActionLink("Configure binding", new SonarConfigureProject())), NO_BINDING_CARD_ID);
    cardPanel.add(centeredLabel(new JLabel("The project binding is invalid"), new ActionLink("Edit binding", new SonarConfigureProject())), INVALID_BINDING_CARD_ID);
    cardPanel.add(centeredLabel(new JLabel("No security hotspot found"), null), NO_SECURITY_HOTSPOT_CARD_ID);
    cardPanel.add(findingsPanel, SECURITY_HOTSPOTS_LIST_CARD_ID);
    setupToolbar(createActionGroup());

    mainPanel.add(cardPanel.getContainer(), BorderLayout.CENTER);
  }

  private static SimpleActionGroup createActionGroup() {
    var sonarLintActions = SonarLintActions.getInstance();
    var actionGroup = new SimpleActionGroup();
    actionGroup.add(sonarLintActions.filterSecurityHotspots());
    actionGroup.add(sonarLintActions.configure());
    return actionGroup;
  }

  private void createFlowsTree() {
    flowsTreeBuilder = new FlowsTreeModelBuilder();
    var model = flowsTreeBuilder.createModel();
    flowsTree = new FlowsTree(project, model);
    flowsTreeBuilder.clearFlows();
    flowsTree.getEmptyText().setText("No security hotspot selected");
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

  public int updateFindings(Map<VirtualFile, Collection<LiveSecurityHotspot>> findings) {
    if (project.isDisposed()) {
      return 0;
    }

    if (status instanceof ValidStatus) {
      securityHotspotCount = securityHotspotTreeBuilder.updateModelWithoutFileNode(findings, "No security hotspots found");
      TreeUtil.expandAll(securityHotspotTree);
      displaySecurityHotspots();

      return securityHotspotCount;
    } else {
      return 0;
    }
  }

  private void createSecurityHotspotsTree() {
    securityHotspotTreeBuilder = new SecurityHotspotTreeModelBuilder();
    var model = securityHotspotTreeBuilder.createModel();
    securityHotspotTree = new SecurityHotspotTree(project, model);
    manageInteraction(securityHotspotTree);
    securityHotspotTree.setRootVisible(false);
    securityHotspotTree.addTreeSelectionListener(this::securityHotspotTreeSelectionChanged);
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

  private void securityHotspotTreeSelectionChanged(TreeSelectionEvent e) {
    if (e.getSource() instanceof SecurityHotspotTree) {
      var selectedHotspotsNodes = securityHotspotTree.getSelectedNodes(LiveSecurityHotspotNode.class, null);
      if (selectedHotspotsNodes.length > 0) {
        updateOnSelect(selectedHotspotsNodes[0].getHotspot());
      } else {
        clearSelectionChanged();
      }
    }
  }

  private void clearSelectionChanged() {
    flowsTreeBuilder.clearFlows();
    flowsTree.getEmptyText().setText("No finding selected");
    rulePanel.clear();
    var highlighting = SonarLintUtils.getService(project, EditorDecorator.class);
    highlighting.removeHighlights();
  }

  public void updateOnSelect(LiveFinding liveFinding) {
    var moduleForFile = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(liveFinding.psiFile().getVirtualFile());
    rulePanel.setSelectedFinding(moduleForFile, liveFinding);
    SonarLintUtils.getService(project, EditorDecorator.class).highlightFinding(liveFinding);
    flowsTree.getEmptyText().setText("Selected security hotspot doesn't have flows");
    flowsTreeBuilder.populateForFinding(liveFinding);
    flowsTree.expandAll();
  }

  public JComponent getPanel() {
    return mainPanel;
  }

  private static JPanel centeredLabel(JLabel textLabel, @Nullable ActionLink actionLink) {
    var labelPanel = new JPanel(new HorizontalLayout(5));
    labelPanel.add(textLabel, HorizontalLayout.CENTER);
    if (actionLink != null) {
      labelPanel.add(actionLink, HorizontalLayout.CENTER);
    }
    return labelPanel;
  }

  private void setupToolbar(ActionGroup group) {
    if (mainToolbar != null) {
      mainToolbar.setTargetComponent(null);
      super.setToolbar(null);
      mainToolbar = null;
    }
    mainToolbar = ActionManager.getInstance().createActionToolbar(TOOLBAR_GROUP_ID, group, false);
    mainToolbar.setTargetComponent(this);
    var toolBarBox = Box.createHorizontalBox();
    toolBarBox.add(mainToolbar.getComponent());
    super.setToolbar(toolBarBox);
    mainToolbar.getComponent().setVisible(true);
  }

  public void populate(SecurityHotspotsStatus status) {
    this.status = status;
    var highlighting = getService(project, EditorDecorator.class);
    highlighting.removeHighlights();
    if (status instanceof NoBinding) {
      cardPanel.show(NO_BINDING_CARD_ID);
    } else if (status instanceof InvalidBinding) {
      cardPanel.show(INVALID_BINDING_CARD_ID);
    } else if (status instanceof ValidStatus) {
      displaySecurityHotspots();
    }
  }

  private void displaySecurityHotspots() {
    if (securityHotspotCount == 0) {
      cardPanel.show(NO_SECURITY_HOTSPOT_CARD_ID);
    } else {
      cardPanel.show(SECURITY_HOTSPOTS_LIST_CARD_ID);
    }
  }

  public boolean trySelectSecurityHotspot(String securityHotspotKey) {
    var foundHotspot = securityHotspotTreeBuilder.findHotspot(securityHotspotKey);
    if (foundHotspot != null) {
      updateOnSelect(foundHotspot);
      return true;
    }
    return false;
  }

  public void hideSonarQubeSecurityHotspots() {
    securityHotspotTreeBuilder.showLocalSecurityHotspots();
  }

  public void hideLocalSecurityHotspots() {
    securityHotspotTreeBuilder.showSonarQubeSecurityHotspots();
  }

  public void showAllSecurityHotspots() {
    securityHotspotTreeBuilder.showAllSecurityHotspots();
  }

  @Override
  // called automatically because the panel is one of the content of the tool window
  public void dispose() {
    // Nothing to do
  }
}
