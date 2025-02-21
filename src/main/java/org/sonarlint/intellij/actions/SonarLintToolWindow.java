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
package org.sonarlint.intellij.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.util.ui.UIUtil;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.actions.filters.SecurityHotspotFilters;
import org.sonarlint.intellij.analysis.AnalysisResult;
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter;
import org.sonarlint.intellij.finding.Finding;
import org.sonarlint.intellij.finding.Issue;
import org.sonarlint.intellij.finding.LiveFinding;
import org.sonarlint.intellij.finding.ShowFinding;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.finding.hotspot.SecurityHotspotsLocalDetectionSupport;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.finding.issue.vulnerabilities.LocalTaintVulnerability;
import org.sonarlint.intellij.finding.issue.vulnerabilities.TaintVulnerabilitiesCache;
import org.sonarlint.intellij.messages.ProjectBindingListener;
import org.sonarlint.intellij.messages.ProjectBindingListenerKt;
import org.sonarlint.intellij.notifications.IncludeResolvedIssueAction;
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications;
import org.sonarlint.intellij.ui.CurrentFilePanel;
import org.sonarlint.intellij.ui.ReportPanel;
import org.sonarlint.intellij.ui.SecurityHotspotsPanel;
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory;
import org.sonarlint.intellij.ui.nodes.LiveSecurityHotspotNode;
import org.sonarlint.intellij.ui.vulnerabilities.TaintVulnerabilitiesPanel;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.hotspot.HotspotStatus;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.ui.UiUtils.runOnUiThread;
import static org.sonarlint.intellij.util.ThreadUtilsKt.runOnPooledThread;

@Service(Service.Level.PROJECT)
public final class SonarLintToolWindow implements ContentManagerListener, ProjectBindingListener {

  private final Project project;
  private Content taintVulnerabilitiesContent;
  private Content securityHotspotsContent;

  public SonarLintToolWindow(Project project) {
    this.project = project;
    project.getMessageBus().connect().subscribe(ProjectBindingListenerKt.getPROJECT_BINDING_TOPIC(), this);
  }

  /**
   * Must run in EDT
   */
  public void openReportTab(AnalysisResult analysisResult) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    this.<ReportPanel>openTab(SonarLintToolWindowFactory.REPORT_TAB_TITLE, panel -> panel.updateFindings(analysisResult));
  }

  public void clearReportTab() {
    updateTab(SonarLintToolWindowFactory.REPORT_TAB_TITLE, ReportPanel::clear);
  }

  public void updateStatusAndApplyCurrentFiltering(String securityHotspotKey, HotspotStatus status) {
    var content = getSecurityHotspotContent();
    if (content != null) {
      var hotspotsPanel = (SecurityHotspotsPanel) content.getComponent();
      var hotspotsCount = hotspotsPanel.updateStatusAndApplyCurrentFiltering(securityHotspotKey, status);
      content.setDisplayName(buildTabName(hotspotsCount, SonarLintToolWindowFactory.SECURITY_HOTSPOTS_TAB_TITLE));
    }
    var contentManager = getToolWindow().getContentManager();
    content = contentManager.findContent(SonarLintToolWindowFactory.REPORT_TAB_TITLE);
    if (content != null) {
      var reportPanel = (ReportPanel) content.getComponent();
      reportPanel.updateStatusForSecurityHotspot(securityHotspotKey, status);
      reportPanel.updateStatusForOldSecurityHotspots(securityHotspotKey, status);
    }
  }

  public void filterSecurityHotspotTab(SecurityHotspotFilters filter) {
    var content = getSecurityHotspotContent();
    if (content != null) {
      var hotspotsPanel = (SecurityHotspotsPanel) content.getComponent();
      var hotspotsCount = hotspotsPanel.filterSecurityHotspots(project, filter);
      content.setDisplayName(buildTabName(hotspotsCount, SonarLintToolWindowFactory.SECURITY_HOTSPOTS_TAB_TITLE));
    }
  }

  public void filterSecurityHotspotTab(boolean isResolved) {
    var content = getSecurityHotspotContent();
    if (content != null) {
      var hotspotsPanel = (SecurityHotspotsPanel) content.getComponent();
      var hotspotsCount = hotspotsPanel.filterSecurityHotspots(project, isResolved);
      content.setDisplayName(buildTabName(hotspotsCount, SonarLintToolWindowFactory.SECURITY_HOTSPOTS_TAB_TITLE));
    }
  }

  private <T> void openTab(String displayName, Consumer<T> tabPanelConsumer) {
    var toolWindow = updateTabAndGet(displayName, tabPanelConsumer);
    if (toolWindow != null) {
      toolWindow.show(() -> selectTab(toolWindow, displayName));
    }
  }

  private <T> ToolWindow updateTabAndGet(String displayName, Consumer<T> tabPanelConsumer) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    var toolWindow = getToolWindow();
    if (toolWindow != null) {
      var contentManager = toolWindow.getContentManager();
      var content = contentManager.findContent(displayName);
      if (content != null) {
        var panel = (T) content.getComponent();
        runOnPooledThread(project, () -> tabPanelConsumer.accept(panel));
      }
    }
    return toolWindow;
  }

  private <T> void updateTab(String displayName, Consumer<T> tabPanelConsumer) {
    var toolWindow = getToolWindow();
    if (toolWindow != null) {
      runOnUiThread(project, () -> {
        var contentManager = toolWindow.getContentManager();
        var content = contentManager.findContent(displayName);
        if (content != null) {
          var panel = (T) content.getComponent();
          tabPanelConsumer.accept(panel);
        }
      });
    }
  }

  public void filterCurrentFileTab(boolean isResolved) {
    this.<CurrentFilePanel>updateTab(SonarLintToolWindowFactory.CURRENT_FILE_TAB_TITLE, panel -> panel.allowResolvedIssues(isResolved));
  }

  public void filterTaintVulnerabilityTab(boolean isResolved) {
    var taintContent = getTaintVulnerabilitiesContent();
    if (taintContent != null) {
      var taintPanel = (TaintVulnerabilitiesPanel) taintContent.getComponent();
      taintPanel.allowResolvedTaintVulnerabilities(isResolved);
      taintContent.setDisplayName(buildTabName(getService(project, TaintVulnerabilitiesCache.class).getFocusAwareCount(isResolved),
        SonarLintToolWindowFactory.TAINT_VULNERABILITIES_TAB_TITLE));
    }
  }

  /**
   * Must run in EDT
   */
  public void openCurrentFileTab() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    openTab(SonarLintToolWindowFactory.CURRENT_FILE_TAB_TITLE);
  }

  /**
   * Must run in EDT
   */
  public void openTaintVulnerabilityTab() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    var toolWindow = getToolWindow();
    if (toolWindow != null) {
      var taintContent = getTaintVulnerabilitiesContent();
      if (taintContent != null) {
        toolWindow.show(() -> toolWindow.getContentManager().setSelectedContent(taintContent));
      }
    }
  }

  public void openOrCloseCurrentFileTab() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    var toolWindow = getToolWindow();
    if (toolWindow != null) {
      var contentManager = toolWindow.getContentManager();
      var content = contentManager.findContent(SonarLintToolWindowFactory.CURRENT_FILE_TAB_TITLE);
      if (content != null) {
        if (content.getComponent().isShowing()) {
          toolWindow.hide();
        } else {
          toolWindow.show(() -> contentManager.setSelectedContent(content));
        }
      }
    }
  }

  public void openSecurityHotspotsTab() {
    openTab(getSecurityHotspotContent());
  }

  public void openLogTab() {
    openTab(SonarLintToolWindowFactory.LOG_TAB_TITLE);
  }

  public void refreshViews() {
    this.updateTab(SonarLintToolWindowFactory.CURRENT_FILE_TAB_TITLE, CurrentFilePanel::refreshView);
    this.updateTab(SonarLintToolWindowFactory.REPORT_TAB_TITLE, ReportPanel::refreshView);

    var hotspotContent = getSecurityHotspotContent();
    if (hotspotContent != null) {
      var hotspotsPanel = (SecurityHotspotsPanel) hotspotContent.getComponent();
      runOnUiThread(project, () -> hotspotContent.setDisplayName(buildTabName(hotspotsPanel.refreshView(), SonarLintToolWindowFactory.SECURITY_HOTSPOTS_TAB_TITLE)));
    }

    var taintContent = getTaintVulnerabilitiesContent();
    if (taintContent != null) {
      var taintPanel = (TaintVulnerabilitiesPanel) taintContent.getComponent();
      taintPanel.applyFocusOnNewCodeSettings();
      taintContent.setDisplayName(buildTabName(getService(project, TaintVulnerabilitiesCache.class).getFocusAwareCount(),
        SonarLintToolWindowFactory.TAINT_VULNERABILITIES_TAB_TITLE));
    }
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

  public void populateTaintVulnerabilitiesTab(List<LocalTaintVulnerability> taintVulnerabilities) {
    var content = getTaintVulnerabilitiesContent();
    if (content != null) {
      var taintVulnerabilitiesPanel = (TaintVulnerabilitiesPanel) content.getComponent();
      taintVulnerabilitiesPanel.populate(taintVulnerabilities);
      content.setDisplayName(buildTabName(getService(project, TaintVulnerabilitiesCache.class).getFocusAwareCount(),
        SonarLintToolWindowFactory.TAINT_VULNERABILITIES_TAB_TITLE));
    }
  }

  public void updateTaintVulnerabilities(Set<UUID> closedTaintVulnerabilityIds, List<LocalTaintVulnerability> addedTaintVulnerabilities,
    List<LocalTaintVulnerability> updatedTaintVulnerabilities) {
    var content = getTaintVulnerabilitiesContent();
    if (content != null) {
      var taintVulnerabilitiesPanel = (TaintVulnerabilitiesPanel) content.getComponent();
      taintVulnerabilitiesPanel.update(closedTaintVulnerabilityIds, addedTaintVulnerabilities, updatedTaintVulnerabilities);
      content.setDisplayName(buildTabName(getService(project, TaintVulnerabilitiesCache.class).getFocusAwareCount(),
        SonarLintToolWindowFactory.TAINT_VULNERABILITIES_TAB_TITLE));
    }
  }

  public void populateSecurityHotspotsTab(SecurityHotspotsLocalDetectionSupport status) {
    var content = getSecurityHotspotContent();
    if (content != null) {
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
    this.<CurrentFilePanel>updateTab(SonarLintToolWindowFactory.CURRENT_FILE_TAB_TITLE,
      panel -> runOnUiThread(project, () -> panel.update(selectedFile, issues)));
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

  public boolean doesSecurityHotspotExist(String securityHotspotKey) {
    var content = getSecurityHotspotContent();
    if (content != null) {
      var sonarLintHotspotsPanel = (SecurityHotspotsPanel) content.getComponent();
      return sonarLintHotspotsPanel.doesSecurityHotspotExist(securityHotspotKey);
    }
    return false;
  }

  public void trySelectIssueForCodeFix(String findingKey) {
    var toolWindow = getToolWindow();
    if (toolWindow != null) {
      var contentManager = toolWindow.getContentManager();
      var content = contentManager.findContent(SonarLintToolWindowFactory.CURRENT_FILE_TAB_TITLE);
      if (content != null) {
        var currentFilePanel = (CurrentFilePanel) content.getComponent();
        var issue = currentFilePanel.getIssueFiltered(findingKey);

        if (issue == null) {
          if (currentFilePanel.doesIssueExist(findingKey)) {
            getService(project, SonarLintProjectNotifications.class).notifyUnableToOpenFinding(
              "The issue could not be opened by SonarQube for IDE due to the applied filters",
              new IncludeResolvedIssueAction()
            );
          } else {
            getService(project, SonarLintProjectNotifications.class).notifyUnableToOpenFinding(
              "The issue was not found",
              new IncludeResolvedIssueAction()
            );
          }
        } else {
          currentFilePanel.trySelectIssueForCodeFix(issue);
        }
      }
    }
  }

  public <T extends Finding> void trySelectIssue(ShowFinding<T> showFinding) {
    var toolWindow = getToolWindow();
    if (toolWindow != null) {
      var contentManager = toolWindow.getContentManager();
      var content = contentManager.findContent(SonarLintToolWindowFactory.CURRENT_FILE_TAB_TITLE);
      if (content != null) {
        var currentFilePanel = (CurrentFilePanel) content.getComponent();
        var issue = currentFilePanel.getIssueFiltered(showFinding.getFindingKey());

        if (issue == null && currentFilePanel.doesIssueExist(showFinding.getFindingKey())) {
          getService(project, SonarLintProjectNotifications.class).notifyUnableToOpenFinding(
            "The issue could not be opened by SonarQube for IDE due to the applied filters",
            new IncludeResolvedIssueAction()
          );
        }

        currentFilePanel.trySelectFilteredIssue(issue, showFinding);
      }
    }
  }

  public <T extends Finding> void trySelectTaintVulnerability(ShowFinding<T> showFinding) {
    var toolWindow = getToolWindow();
    if (toolWindow != null) {
      var taintContent = getTaintVulnerabilitiesContent();
      if (taintContent != null) {
        var taintPanel = (TaintVulnerabilitiesPanel) taintContent.getComponent();
        taintPanel.trySelectFilteredTaintVulnerability(showFinding);
      }
    }
  }

  public boolean trySelectSecurityHotspot(String securityHotspotKey) {
    var content = getSecurityHotspotContent();
    if (content != null) {
      var sonarLintHotspotsPanel = (SecurityHotspotsPanel) content.getComponent();
      return sonarLintHotspotsPanel.trySelectFilteredSecurityHotspot(securityHotspotKey);
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
      runOnUiThread(project, () -> {
        var count = hotspotsPanel.updateHotspots(currentSecurityHotspotsPerOpenFile);
        content.setDisplayName(buildTabName(count, SonarLintToolWindowFactory.SECURITY_HOTSPOTS_TAB_TITLE));
      });
    }
  }

  public Collection<LiveSecurityHotspot> getDisplayedSecurityHotspotsForFile(VirtualFile file) {
    var toolWindow = getToolWindow();
    if (toolWindow != null && toolWindow.isVisible() && securityHotspotsContent != null && securityHotspotsContent.isSelected()) {
      var securityHotspotPanel = (SecurityHotspotsPanel) securityHotspotsContent.getComponent();
      return securityHotspotPanel.getDisplayedNodesForFile(file).stream().map(LiveSecurityHotspotNode::getHotspot).toList();
    }
    return Collections.emptyList();
  }

  public void markAsResolved(Issue issue) {
    if (issue instanceof LiveIssue liveIssue) {
      this.<CurrentFilePanel>updateTab(SonarLintToolWindowFactory.CURRENT_FILE_TAB_TITLE, panel -> panel.remove(liveIssue));
      this.updateTab(SonarLintToolWindowFactory.CURRENT_FILE_TAB_TITLE, CurrentFilePanel::refreshModel);
      this.<ReportPanel>updateTab(SonarLintToolWindowFactory.REPORT_TAB_TITLE, panel -> panel.remove(liveIssue));
    } else {
      var content = getTaintVulnerabilitiesContent();
      if (content != null) {
        ((TaintVulnerabilitiesPanel) content.getComponent()).remove((LocalTaintVulnerability) issue);
        ((TaintVulnerabilitiesPanel) content.getComponent()).switchCard();
      }
    }
  }

  public void reopenIssue(Issue issue) {
    if (issue instanceof LiveIssue liveIssue) {
      this.<CurrentFilePanel>updateTab(SonarLintToolWindowFactory.CURRENT_FILE_TAB_TITLE, panel -> panel.remove(liveIssue));
      this.updateTab(SonarLintToolWindowFactory.CURRENT_FILE_TAB_TITLE, CurrentFilePanel::refreshModel);
    } else if (issue instanceof LocalTaintVulnerability taintVulnerability) {
      var taintContent = getTaintVulnerabilitiesContent();
      if (taintContent != null) {
        var taintPanel = (TaintVulnerabilitiesPanel) taintContent.getComponent();
        taintPanel.remove(taintVulnerability);
      }
    }
  }

  @Override
  public void selectionChanged(@NotNull ContentManagerEvent event) {
    // Introduced in the context of Security Hotspot to trigger analysis when opening the SH tab and when tabbing out to remove highlighting
    getService(project, CodeAnalyzerRestarter.class).refreshOpenFiles();
  }

  private Content getSecurityHotspotContent() {
    var toolWindow = getToolWindow();
    if (securityHotspotsContent == null && toolWindow != null) {
      securityHotspotsContent = toolWindow.getContentManager()
        .findContent(buildTabName(0, SonarLintToolWindowFactory.SECURITY_HOTSPOTS_TAB_TITLE));
    }
    return securityHotspotsContent;
  }

  private Content getTaintVulnerabilitiesContent() {
    var toolWindow = getToolWindow();
    if (taintVulnerabilitiesContent == null && toolWindow != null) {
      taintVulnerabilitiesContent = toolWindow.getContentManager()
        .findContent(buildTabName(0, SonarLintToolWindowFactory.TAINT_VULNERABILITIES_TAB_TITLE));
    }
    return taintVulnerabilitiesContent;
  }

  @Override
  public void bindingChanged() {
    runOnPooledThread(project, this::refreshViews);
  }
}
