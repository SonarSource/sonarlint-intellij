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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.tools.SimpleActionGroup;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBPanelWithEmptyText;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.TreeSelectionModel;
import org.sonarlint.intellij.actions.OpenSecurityHotspotDocumentationAction;
import org.sonarlint.intellij.actions.SonarConfigureProject;
import org.sonarlint.intellij.actions.filters.SecurityHotspotFilters;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.editor.EditorDecorator;
import org.sonarlint.intellij.finding.LiveFinding;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.finding.hotspot.NotSupported;
import org.sonarlint.intellij.finding.hotspot.SecurityHotspotsLocalDetectionSupport;
import org.sonarlint.intellij.finding.hotspot.Supported;
import org.sonarlint.intellij.ui.nodes.LiveSecurityHotspotNode;
import org.sonarlint.intellij.ui.tree.SecurityHotspotTree;
import org.sonarlint.intellij.ui.tree.SecurityHotspotTreeModelBuilder;
import org.sonarlint.intellij.util.SonarLintActions;
import org.sonarlint.intellij.util.SummaryNodeType;
import org.sonarsource.sonarlint.core.clientapi.backend.hotspot.HotspotStatus;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.ui.SonarLintToolWindowFactory.createSplitter;

public class SecurityHotspotsPanel extends SimpleToolWindowPanel implements Disposable {
  private static final String NOT_SUPPORTED_CARD_ID = "NOT_SUPPORTED_CARD";
  private static final String NO_SECURITY_HOTSPOT_CARD_ID = "NO_SECURITY_HOTSPOT_CARD_ID";
  private static final String NO_SECURITY_HOTSPOT_FILTERED_CARD_ID = "NO_SECURITY_HOTSPOT_FILTERED_CARD_ID";
  private static final String SECURITY_HOTSPOTS_LIST_CARD_ID = "SECURITY_HOTSPOTS_LIST_CARD_ID";
  private static final String TOOLBAR_GROUP_ID = "SecurityHotspot";
  private static final String SPLIT_PROPORTION_PROPERTY = "SONARLINT_ANALYSIS_RESULTS_SPLIT_PROPORTION";
  protected SecurityHotspotTreeModelBuilder securityHotspotTreeBuilder;
  protected Tree securityHotspotTree;
  private final JPanel mainPanel;
  private final Project project;
  private final CardPanel cardPanel;
  private ActionToolbar mainToolbar;
  private SecurityHotspotsLocalDetectionSupport status;
  private int securityHotspotCount;
  private JBPanelWithEmptyText notSupportedPanel;
  private AnAction sonarConfigureProject;
  private FindingDetailsPanel findingDetailsPanel;

  public SecurityHotspotsPanel(Project project) {
    super(false, true);
    this.project = project;
    securityHotspotCount = 0;
    cardPanel = new CardPanel();
    mainPanel = new JPanel(new BorderLayout());

    createSecurityHotspotsTree();
    createFindingDetailsPanel();
    initPanel();

    super.setContent(mainPanel);
  }

  private void initPanel() {
    var treePanel = new JPanel(new VerticalFlowLayout(0, 0));
    treePanel.add(securityHotspotTree);

    findingDetailsPanel.setMinimumSize(new Dimension(350, 200));
    var findingsPanel = new JPanel(new BorderLayout());
    findingsPanel.add(createSplitter(project, this, this,
      ScrollPaneFactory.createScrollPane(treePanel), findingDetailsPanel, SPLIT_PROPORTION_PROPERTY, 0.5f));

    sonarConfigureProject = new SonarConfigureProject();
    notSupportedPanel = centeredLabel("Security Hotspots are currently not supported", "Configure Binding", sonarConfigureProject);
    cardPanel.add(notSupportedPanel, NOT_SUPPORTED_CARD_ID);
    cardPanel.add(centeredLabel("No Security Hotspots found for currently opened files in the latest analysis", null, null), NO_SECURITY_HOTSPOT_CARD_ID);
    cardPanel.add(centeredLabel("No Security Hotspots shown due to the current filtering", null, null), NO_SECURITY_HOTSPOT_FILTERED_CARD_ID);
    cardPanel.add(findingsPanel, SECURITY_HOTSPOTS_LIST_CARD_ID);

    setupToolbar(createActionGroup());
    mainPanel.add(cardPanel.getContainer(), BorderLayout.CENTER);
  }

  private static SimpleActionGroup createActionGroup() {
    var sonarLintActions = SonarLintActions.getInstance();
    var actionGroup = new SimpleActionGroup();
    actionGroup.add(ActionManager.getInstance().getAction("SonarLint.SetFocusNewCode"));
    actionGroup.add(sonarLintActions.filterSecurityHotspots());
    actionGroup.add(sonarLintActions.includeResolvedHotspotAction());
    actionGroup.add(sonarLintActions.configure());
    actionGroup.add(new OpenSecurityHotspotDocumentationAction());
    return actionGroup;
  }

  private void createFindingDetailsPanel() {
    findingDetailsPanel = new FindingDetailsPanel(project, this, FindingKind.SECURITY_HOTSPOT);
  }

  public int updateFindings(Map<VirtualFile, Collection<LiveSecurityHotspot>> findings) {
    if (project.isDisposed()) {
      return 0;
    }

    if (status instanceof Supported) {
      securityHotspotCount = securityHotspotTreeBuilder.updateModelWithoutFileNode(findings, "No Security Hotspots found");
      TreeUtil.expandAll(securityHotspotTree);
      displaySecurityHotspots();

      return displaySecurityHotspotsAfterFiltering(securityHotspotTreeBuilder.applyCurrentFiltering(project));
    } else {
      return 0;
    }
  }

  private void createSecurityHotspotsTree() {
    securityHotspotTreeBuilder = new SecurityHotspotTreeModelBuilder();
    var model = securityHotspotTreeBuilder.createModel(SummaryNodeType.NEW_SECURITY_HOTSPOT);
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
    findingDetailsPanel.clear();
    var highlighting = SonarLintUtils.getService(project, EditorDecorator.class);
    highlighting.removeHighlights();
  }

  public void updateOnSelect(LiveFinding liveFinding) {
    findingDetailsPanel.show(liveFinding);
  }

  public JComponent getPanel() {
    return mainPanel;
  }

  private static JBPanelWithEmptyText centeredLabel(String textLabel, @Nullable String actionText, @Nullable AnAction action) {
    var labelPanel = new JBPanelWithEmptyText(new HorizontalLayout(5));
    var text = labelPanel.getEmptyText();
    text.setText(textLabel);
    if (action != null && actionText != null) {
      text.appendLine(actionText, SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
        ignore -> ActionUtil.invokeAction(action, labelPanel, CurrentFilePanel.SONARLINT_TOOLWINDOW_ID, null, null));
    }
    return labelPanel;
  }

  private void updateNotSupportedText(String newText) {
    var text = notSupportedPanel.getEmptyText();
    text.setText(newText);
    if (sonarConfigureProject != null) {
      text.appendLine("Configure Binding", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
        ignore -> ActionUtil.invokeAction(sonarConfigureProject, notSupportedPanel, CurrentFilePanel.SONARLINT_TOOLWINDOW_ID, null, null));
    }
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

  public void populate(SecurityHotspotsLocalDetectionSupport status) {
    this.status = status;
    var highlighting = getService(project, EditorDecorator.class);
    highlighting.removeHighlights();
    if (status instanceof NotSupported) {
      updateNotSupportedText(((NotSupported) status).getReason());
      cardPanel.show(NOT_SUPPORTED_CARD_ID);
    } else if (status instanceof Supported) {
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

  private int displaySecurityHotspotsAfterFiltering(int filteredCount) {
    if (status instanceof Supported) {
      if (filteredCount == 0 && securityHotspotCount > 0) {
        cardPanel.show(NO_SECURITY_HOTSPOT_FILTERED_CARD_ID);
      } else if (filteredCount == 0 && securityHotspotCount == 0) {
        cardPanel.show(NO_SECURITY_HOTSPOT_CARD_ID);
      } else {
        cardPanel.show(SECURITY_HOTSPOTS_LIST_CARD_ID);
      }
      return filteredCount;
    }
    return 0;
  }

  public boolean trySelectFilteredSecurityHotspot(String securityHotspotKey) {
    var foundHotspot = securityHotspotTreeBuilder.findFilteredHotspotByKey(securityHotspotKey);
    if (foundHotspot != null) {
      updateOnSelect(foundHotspot);
      return true;
    }
    return false;
  }

  public boolean doesSecurityHotspotExist(String securityHotspotKey) {
    return securityHotspotTreeBuilder.findHotspotByKey(securityHotspotKey).isPresent();
  }

  public int updateStatusAndApplyCurrentFiltering(String securityHotspotKey, HotspotStatus status) {
    return displaySecurityHotspotsAfterFiltering(securityHotspotTreeBuilder.updateStatusAndApplyCurrentFiltering(project, securityHotspotKey, status));
  }

  public void selectAndHighlightSecurityHotspot(LiveSecurityHotspot securityHotspot) {
    updateOnSelect(securityHotspot);
  }

  public int filterSecurityHotspots(Project project, SecurityHotspotFilters filter) {
    return displaySecurityHotspotsAfterFiltering(securityHotspotTreeBuilder.filterSecurityHotspots(project, filter));
  }

  public int filterSecurityHotspots(Project project, boolean isResolved) {
    return displaySecurityHotspotsAfterFiltering(securityHotspotTreeBuilder.filterSecurityHotspots(project, isResolved));
  }

  public void selectLocationsTab() {
    findingDetailsPanel.selectLocationsTab();
  }

  public void selectRulesTab() {
    findingDetailsPanel.selectRulesTab();
  }

  public Collection<LiveSecurityHotspotNode> getDisplayedNodesForFile(VirtualFile file) {
    return securityHotspotTreeBuilder.getFilteredNodes()
      .stream()
      .filter(node -> node.getHotspot().getFile().equals(file))
      .collect(Collectors.toList());
  }

  @Override
  // called automatically because the panel is one of the content of the tool window
  public void dispose() {
    // Nothing to do
  }
}
