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
package org.sonarlint.intellij.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.ui.UIUtil;
import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.actions.filters.SecurityHotspotFilters;
import org.sonarlint.intellij.analysis.AnalysisResult;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter;
import org.sonarlint.intellij.finding.LiveFinding;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.finding.hotspot.SecurityHotspotsLocalDetectionSupport;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability;
import org.sonarlint.intellij.finding.issue.vulnerabilities.TaintVulnerabilitiesStatus;
import org.sonarlint.intellij.ui.ContentManagerListenerAdapter;
import org.sonarlint.intellij.ui.CurrentFilePanel;
import org.sonarlint.intellij.ui.ReportPanel;
import org.sonarlint.intellij.ui.SecurityHotspotsPanel;
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory;
import org.sonarlint.intellij.ui.vulnerabilities.TaintVulnerabilitiesPanel;
import org.sonarsource.sonarlint.core.commons.RuleType;

public class SonarLintToolWindow implements ContentManagerListenerAdapter {

  private final Project project;
  private Content taintVulnerabilitiesContent;
  private Content securityHotspotsContent;

  public SonarLintToolWindow(Project project) {
    this.project = project;
  }

  /**
   * Must run in EDT
   */
  public void openReportTab(AnalysisResult analysisResult) {
    this.<ReportPanel>openTab(SonarLintToolWindowFactory.REPORT_TAB_TITLE, panel -> panel.updateFindings(analysisResult));
  }

  public void clearReportTab() {
    updateTab(SonarLintToolWindowFactory.REPORT_TAB_TITLE, ReportPanel::clear);
  }

  public void removeSecurityHotspotFromList(String securityHotspotKey) {
    var content = getSecurityHotspotContent();
    if (content != null) {
      var hotspotsPanel = (SecurityHotspotsPanel) content.getComponent();
      var hotspotsCount = hotspotsPanel.removeSecurityHotspotFromList(securityHotspotKey);
      content.setDisplayName(buildTabName(hotspotsCount, SonarLintToolWindowFactory.SECURITY_HOTSPOTS_TAB_TITLE));
    }
  }

  public void filterSecurityHotspotTab(SecurityHotspotFilters filter) {
    var content = getSecurityHotspotContent();
    if (content != null) {
      var hotspotsPanel = (SecurityHotspotsPanel) content.getComponent();
      var hotspotsCount = hotspotsPanel.filterSecurityHotspots(filter);
      content.setDisplayName(buildTabName(hotspotsCount, SonarLintToolWindowFactory.SECURITY_HOTSPOTS_TAB_TITLE));
    }
  }

  private <T> void openTab(String displayName, Consumer<T> tabPanelConsumer) {
    var toolWindow = updateTab(displayName, tabPanelConsumer);
    if (toolWindow != null) {
      toolWindow.show(() -> selectTab(toolWindow, displayName));
    }
  }

  private <T> ToolWindow updateTab(String displayName, Consumer<T> tabPanelConsumer) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    var toolWindow = getToolWindow();
    if (toolWindow != null) {
      var contentManager = toolWindow.getContentManager();
      var content = contentManager.findContent(displayName);
      var panel = (T) content.getComponent();
      tabPanelConsumer.accept(panel);
    }
    return toolWindow;
  }

  /**
   * Must run in EDT
   */
  public void openCurrentFileTab() {
    openTab(SonarLintToolWindowFactory.CURRENT_FILE_TAB_TITLE);
  }

  public void openSecurityHotspotsTab() {
    openTab(getSecurityHotspotContent());
  }

  private void openTab(String name) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    var toolWindow = getToolWindow();
    if (toolWindow != null) {
      toolWindow.show(() -> selectTab(toolWindow, name));
    }
  }

  private void openTab(Content content) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    var toolWindow = getToolWindow();
    if (toolWindow != null) {
      toolWindow.show(() -> toolWindow.getContentManager().setSelectedContent(content));
    }
  }

  private ToolWindow getToolWindow() {
    return SonarLintToolWindowFactory.getSonarLintToolWindow(project);
  }

  private Content getTaintVulnerabilitiesContent() {
    var toolWindow = getToolWindow();
    if (taintVulnerabilitiesContent == null && toolWindow != null) {
      taintVulnerabilitiesContent = toolWindow.getContentManager()
        .findContent(buildTabName(0, SonarLintToolWindowFactory.TAINT_VULNERABILITIES_TAB_TITLE));
    }
    return taintVulnerabilitiesContent;
  }

  public void populateTaintVulnerabilitiesTab(TaintVulnerabilitiesStatus status) {
    var content = getTaintVulnerabilitiesContent();
    if (content != null) {
      content.setDisplayName(buildTabName(status.count(), SonarLintToolWindowFactory.TAINT_VULNERABILITIES_TAB_TITLE));
      var taintVulnerabilitiesPanel = (TaintVulnerabilitiesPanel) content.getComponent();
      taintVulnerabilitiesPanel.populate(status);
    }
  }

  private Content getSecurityHotspotContent() {
    var toolWindow = getToolWindow();
    if (securityHotspotsContent == null && toolWindow != null) {
      securityHotspotsContent = toolWindow.getContentManager()
        .findContent(buildTabName(0, SonarLintToolWindowFactory.SECURITY_HOTSPOTS_TAB_TITLE));
    }
    return securityHotspotsContent;
  }

  public void populateSecurityHotspotsTab(SecurityHotspotsLocalDetectionSupport status) {
    var content = getSecurityHotspotContent();
    if (content != null) {
      content.setDisplayName(buildTabName(0, SonarLintToolWindowFactory.SECURITY_HOTSPOTS_TAB_TITLE));
      var hotspotsPanel = (SecurityHotspotsPanel) content.getComponent();
      hotspotsPanel.populate(status);
    }
  }

  public static String buildTabName(int count, String tabName) {
    if (count == 0) {
      return tabName;
    }
    return "<html><body>" + tabName + "<font color=\"" + ColorUtil.toHtmlColor(UIUtil.getInactiveTextColor()) + "\"> " + count
      + "</font></body></html>";
  }

  public void showTaintVulnerabilityDescription(LocalTaintVulnerability vulnerability) {
    var content = getTaintVulnerabilitiesContent();
    openTab(content);
    ((TaintVulnerabilitiesPanel) content.getComponent()).setSelectedVulnerability(vulnerability);
  }

  private static void selectTab(ToolWindow toolWindow, String tabId) {
    var contentManager = toolWindow.getContentManager();
    var content = contentManager.findContent(tabId);
    if (content != null) {
      contentManager.setSelectedContent(content);
    }
  }

  public void updateCurrentFileTab(@Nullable VirtualFile selectedFile, @Nullable Collection<LiveIssue> issues) {
    this.<CurrentFilePanel>updateTab(SonarLintToolWindowFactory.CURRENT_FILE_TAB_TITLE, panel -> panel.update(selectedFile, issues));
  }

  private void showIssue(LiveIssue liveIssue, Consumer<CurrentFilePanel> selectTab) {
    openCurrentFileTab();
    selectTab(getToolWindow(), SonarLintToolWindowFactory.CURRENT_FILE_TAB_TITLE);
    var contentManager = getToolWindow().getContentManager();
    var content = contentManager.findContent(SonarLintToolWindowFactory.CURRENT_FILE_TAB_TITLE);
    var currentFilePanel = (CurrentFilePanel) content.getComponent();
    currentFilePanel.setSelectedIssue(liveIssue);
    selectTab.accept(currentFilePanel);
  }

  private void showSecurityHotspot(LiveSecurityHotspot liveSecurityHotspot, Consumer<SecurityHotspotsPanel> selectTab) {
    var content = getSecurityHotspotContent();
    if (content != null) {
      var hotspotsPanel = (SecurityHotspotsPanel) content.getComponent();
      hotspotsPanel.selectAndHighlightSecurityHotspot(liveSecurityHotspot);
      selectTab.accept(hotspotsPanel);
    }
  }

  public void showFindingDescription(LiveFinding liveFinding) {
    if (liveFinding.getType() == RuleType.SECURITY_HOTSPOT) {
      showSecurityHotspotDescription((LiveSecurityHotspot) liveFinding);
    } else {
      showIssueDescription((LiveIssue) liveFinding);
    }
  }

  public void showIssueDescription(LiveIssue liveIssue) {
    showIssue(liveIssue, CurrentFilePanel::selectRulesTab);
  }

  public void showSecurityHotspotDescription(LiveSecurityHotspot liveSecurityHotspot) {
    showSecurityHotspot(liveSecurityHotspot, SecurityHotspotsPanel::selectRulesTab);
  }

  public void showFindingLocations(LiveFinding liveFinding) {
    if (liveFinding.getType() == RuleType.SECURITY_HOTSPOT) {
      showSecurityHotspotLocations((LiveSecurityHotspot) liveFinding);
    } else {
      showIssueLocations((LiveIssue) liveFinding);
    }
  }

  public void showIssueLocations(LiveIssue liveIssue) {
    showIssue(liveIssue, CurrentFilePanel::selectLocationsTab);
  }

  public void showSecurityHotspotLocations(LiveSecurityHotspot liveSecurityHotspot) {
    showSecurityHotspot(liveSecurityHotspot, SecurityHotspotsPanel::selectLocationsTab);
  }

  public boolean trySelectSecurityHotspot(String securityHotspotKey) {
    var content = getSecurityHotspotContent();
    if (content != null) {
      var sonarLintHotspotsPanel = (SecurityHotspotsPanel) content.getComponent();
      return sonarLintHotspotsPanel.trySelectSecurityHotspot(securityHotspotKey);
    }
    return false;
  }

  public void bringToFront() {
    var toolWindow = SonarLintToolWindowFactory.getSonarLintToolWindow(project);
    if (toolWindow != null) {
      var component = toolWindow.getComponent();
      IdeFocusManager.getInstance(project).requestFocus(component, true);
      var window = SwingUtilities.getWindowAncestor(component);
      if (window != null) {
        window.toFront();
      }
    }
  }

  public void updateOnTheFlySecurityHotspots(@NotNull Map<VirtualFile, Collection<LiveSecurityHotspot>> currentSecurityHotspotsPerOpenFile) {
    var content = getSecurityHotspotContent();
    if (content != null) {
      var hotspotsPanel = (SecurityHotspotsPanel) content.getComponent();
      var count = hotspotsPanel.updateFindings(currentSecurityHotspotsPerOpenFile);
      content.setDisplayName(buildTabName(count, SonarLintToolWindowFactory.SECURITY_HOTSPOTS_TAB_TITLE));
    }
  }

  public boolean isSecurityHotspotsTabActive() {
    var toolWindow = getToolWindow();
    if (toolWindow != null && securityHotspotsContent != null) {
      return toolWindow.isVisible() && securityHotspotsContent.isSelected();
    }
    return false;
  }

  @Override
  public void selectionChanged(@NotNull ContentManagerEvent event) {
    // Introduced in the context of security hotspot to trigger analysis when opening the SH tab and when tabbing out to remove highlighting
    SonarLintUtils.getService(project, CodeAnalyzerRestarter.class).refreshOpenFiles();
  }
}
