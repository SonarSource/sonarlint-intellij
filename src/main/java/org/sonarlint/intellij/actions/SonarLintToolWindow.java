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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.analysis.AnalysisResult;
import org.sonarlint.intellij.editor.EditorDecorator;
import org.sonarlint.intellij.finding.hotspot.FoundSecurityHotspots;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.finding.hotspot.SecurityHotspotsStatus;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability;
import org.sonarlint.intellij.finding.issue.vulnerabilities.TaintVulnerabilitiesStatus;
import org.sonarlint.intellij.ui.ContentManagerListenerAdapter;
import org.sonarlint.intellij.ui.CurrentFilePanel;
import org.sonarlint.intellij.ui.ReportPanel;
import org.sonarlint.intellij.ui.SonarLintHotspotsListPanel;
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory;
import org.sonarlint.intellij.ui.vulnerabilities.TaintVulnerabilitiesPanel;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class SonarLintToolWindow implements ContentManagerListenerAdapter {

  private final Project project;
  private Content taintVulnerabilitiesContent;
  private Content securityHotspotsContent;
  private AnalysisResult analysisResult;
  private Collection<LiveSecurityHotspot> liveSecurityHotspots = new ArrayList<>();

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

  private <T> void openTab(String displayName, Consumer<T> tabPanelConsumer) {
    var toolWindow = updateTab(displayName, tabPanelConsumer);
    if (toolWindow != null) {
      selectTab(toolWindow, displayName);
    }
  }

  private <T> ToolWindow updateTab(String displayName, Consumer<T> tabPanelConsumer) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    var toolWindow = getToolWindow();
    if (toolWindow != null) {
      toolWindow.show(() -> {
        var contentManager = getToolWindow().getContentManager();
        var content = contentManager.findContent(displayName);
        var panel = (T) content.getComponent();
        tabPanelConsumer.accept(panel);
      });
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
    if (taintVulnerabilitiesContent == null) {
      taintVulnerabilitiesContent = getToolWindow().getContentManager()
        .findContent(buildTabName(0, SonarLintToolWindowFactory.TAINT_VULNERABILITIES_TAB_TITLE));
    }
    return taintVulnerabilitiesContent;
  }

  public void populateTaintVulnerabilitiesTab(TaintVulnerabilitiesStatus status) {
    if (getToolWindow() == null) {
      return;
    }
    var content = getTaintVulnerabilitiesContent();
    content.setDisplayName(buildTabName(status.count(), SonarLintToolWindowFactory.TAINT_VULNERABILITIES_TAB_TITLE));
    var taintVulnerabilitiesPanel = (TaintVulnerabilitiesPanel) content.getComponent();
    taintVulnerabilitiesPanel.populate(status);
  }

  private Content getSecurityHotspotContent() {
    var toolWindow = getToolWindow();
    if (securityHotspotsContent == null && toolWindow != null) {
      securityHotspotsContent = toolWindow.getContentManager()
        .findContent(buildTabName(0, SonarLintToolWindowFactory.SECURITY_HOTSPOTS_TAB_TITLE));
    }
    return securityHotspotsContent;
  }

  public void populateSecurityHotspotsTab(SecurityHotspotsStatus status) {
    if (getToolWindow() == null) {
      return;
    }
    var content = getSecurityHotspotContent();
    content.setDisplayName(buildTabName(status.count(), SonarLintToolWindowFactory.SECURITY_HOTSPOTS_TAB_TITLE));
    var hotspotsPanel = (SonarLintHotspotsListPanel) content.getComponent();
    hotspotsPanel.populate(status);
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

  public void updateHotspotsTab(AnalysisResult analysisResult) {
    var content = getSecurityHotspotContent();
    content.setDisplayName(buildTabName(1, SonarLintToolWindowFactory.SECURITY_HOTSPOTS_TAB_TITLE));
    var hotspotsPanel = (SonarLintHotspotsListPanel) content.getComponent();
    hotspotsPanel.updateFindings(analysisResult);
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

  public void showIssueDescription(LiveIssue liveIssue) {
    showIssue(liveIssue, CurrentFilePanel::selectRulesTab);
  }

  public void showIssueLocations(LiveIssue liveIssue) {
    showIssue(liveIssue, CurrentFilePanel::selectLocationsTab);
  }


  public boolean trySelectSecurityHotspot(String securityHotspotKey) {
    var foundHotspot = liveSecurityHotspots.stream().filter(sh -> securityHotspotKey.equals(sh.getServerFindingKey())).findFirst();
    if (foundHotspot.isPresent()) {
      var sonarLintHotspotsPanel = (SonarLintHotspotsPanel) getSecurityHotspotContent().getComponent();
      sonarLintHotspotsPanel.setSelectedSecurityHotspot(foundHotspot.get());
      return true;
    }
    //TODO replace the null
  }

  public void renderHotspotFindings(Map<VirtualFile, Collection<LiveSecurityHotspot>> hotspotsMap) {
    mergeAndSortHotspot(hotspotsMap);
    renderFindings(liveSecurityHotspots);
  }

  public void renderFindings(Collection<LiveSecurityHotspot> securityHotspots) {
    var status = new FoundSecurityHotspots(securityHotspots);
    populateSecurityHotspotsTab(status);
  }

  private void mergeAndSortHotspot(Map<VirtualFile, Collection<LiveSecurityHotspot>> hotspotsMap) {
    liveSecurityHotspots.clear();
    hotspotsMap.forEach((file, list) ->
      liveSecurityHotspots.addAll(list)
    );
    liveSecurityHotspots = liveSecurityHotspots.stream()
      .sorted((h1, h2) -> h2.getVulnerabilityProbability().getScore() - h1.getVulnerabilityProbability().getScore())
      .collect(Collectors.toList());
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

  @Override
  public void contentRemoved(@NotNull ContentManagerEvent event) {
    // only hotspots tab is removable
    getService(project, EditorDecorator.class).removeHighlights();
  }

  public Collection<LiveSecurityHotspot> getHotspotList() {
    return this.liveSecurityHotspots;
  }

}
