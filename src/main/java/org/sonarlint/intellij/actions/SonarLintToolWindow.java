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
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.ui.UIUtil;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.editor.EditorDecorator;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.issue.hotspot.LocalHotspot;
import org.sonarlint.intellij.issue.vulnerabilities.LocalTaintVulnerability;
import org.sonarlint.intellij.issue.vulnerabilities.TaintVulnerabilitiesStatus;
import org.sonarlint.intellij.ui.ContentManagerListenerAdapter;
import org.sonarlint.intellij.ui.SonarLintHotspotsPanel;
import org.sonarlint.intellij.ui.CurrentFilePanel;
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory;
import org.sonarlint.intellij.ui.vulnerabilities.TaintVulnerabilitiesPanel;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class SonarLintToolWindow implements ContentManagerListenerAdapter {
  private static final String TAB_HOTSPOTS = "Security Hotspots";
  private static final int HOTSPOTS_TAB_INDEX = 2;

  private final Project project;
  private LocalHotspot activeHotspot;
  private Content taintVulnerabilitiesContent;

  public SonarLintToolWindow(Project project) {
    this.project = project;
  }

  /**
   * Must run in EDT
   */
  public void openReportTab() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    var toolWindow = getToolWindow();
    if (toolWindow != null) {
      toolWindow.show(() -> selectTab(toolWindow, SonarLintToolWindowFactory.REPORT_TAB_TITLE));
    }
  }

  /**
   * Must run in EDT
   */
  public void openCurrentFileTab() {
    openTab(SonarLintToolWindowFactory.CURRENT_FILE_TAB_TITLE);
  }

  public void openTab(String name) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    var toolWindow = getToolWindow();
    if (toolWindow != null) {
      toolWindow.show(() -> selectTab(toolWindow, name));
    }
  }

  public void openTab(Content content) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    var toolWindow = getToolWindow();
    if (toolWindow != null) {
      toolWindow.show(() -> toolWindow.getContentManager().setSelectedContent(content));
    }
  }

  private ToolWindow getToolWindow() {
    var toolWindowManager = ToolWindowManager.getInstance(project);
    return toolWindowManager.getToolWindow(SonarLintToolWindowFactory.TOOL_WINDOW_ID);
  }

  private Content getTaintVulnerabilitiesContent() {
    if (taintVulnerabilitiesContent == null) {
      taintVulnerabilitiesContent = getToolWindow().getContentManager()
        .findContent(buildVulnerabilitiesTabName(0));
    }
    return taintVulnerabilitiesContent;
  }

  public void populateTaintVulnerabilitiesTab(TaintVulnerabilitiesStatus status) {
    if (getToolWindow() == null) {
      return;
    }
    var content = getTaintVulnerabilitiesContent();
    content.setDisplayName(buildVulnerabilitiesTabName(status.count()));
    var taintVulnerabilitiesPanel = (TaintVulnerabilitiesPanel) content.getComponent();
    taintVulnerabilitiesPanel.populate(status);
  }

  public static String buildVulnerabilitiesTabName(int count) {
    if (count == 0) {
      return SonarLintToolWindowFactory.TAINT_VULNERABILITIES_TAB_TITLE;
    }
    return "<html><body>" + SonarLintToolWindowFactory.TAINT_VULNERABILITIES_TAB_TITLE + "<font color=\"" + ColorUtil.toHtmlColor(UIUtil.getInactiveTextColor()) + "\"> " + count
      + "</font></body></html>";
  }

  public void showTaintVulnerabilityDescription(LocalTaintVulnerability vulnerability) {
    var content = getTaintVulnerabilitiesContent();
    openTab(content);
    ((TaintVulnerabilitiesPanel)content.getComponent()).setSelectedVulnerability(vulnerability);
  }

  private static void selectTab(ToolWindow toolWindow, String tabId) {
    var contentManager = toolWindow.getContentManager();
    var content = contentManager.findContent(tabId);
    if (content != null) {
      contentManager.setSelectedContent(content);
    }
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

  public void show(LocalHotspot localHotspot) {
    activeHotspot = localHotspot;
    if (getToolWindow() == null) {
      // can happen if we try to show while the project is opening
      // we will show the hotspot when the tool window is built
      return;
    }
    var content = ensureHotspotsTabCreated();
    openTab(TAB_HOTSPOTS);
    var sonarLintHotspotsPanel = (SonarLintHotspotsPanel) content.getComponent();
    sonarLintHotspotsPanel.setHotspot(localHotspot);
    bringIdeToFront(project);
  }

  public LocalHotspot getActiveHotspot() {
    return activeHotspot;
  }

  private void bringIdeToFront(Project project) {
    var component = getToolWindow().getComponent();
    IdeFocusManager.getInstance(project).requestFocus(component, true);
    var window = SwingUtilities.getWindowAncestor(component);
    if (window != null) {
      window.toFront();
    }
  }

  private Content ensureHotspotsTabCreated() {
    var contentManager = getToolWindow().getContentManager();
    var existingContent = contentManager.findContent(TAB_HOTSPOTS);
    if (existingContent != null) {
      return existingContent;
    }
    var hotspotsContent = contentManager.getFactory()
      .createContent(
        new SonarLintHotspotsPanel(project),
        TAB_HOTSPOTS,
        false);
    contentManager.addContent(hotspotsContent, HOTSPOTS_TAB_INDEX);
    return hotspotsContent;
  }

  @Override
  public void contentRemoved(@NotNull ContentManagerEvent event) {
    // only hotspots tab is removable
    getService(project, EditorDecorator.class).removeHighlights();
  }
}
