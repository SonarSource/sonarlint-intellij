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
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.ContentManagerListener;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.analysis.AnalysisResult;
import org.sonarlint.intellij.finding.Finding;
import org.sonarlint.intellij.finding.ShowFinding;
import org.sonarlint.intellij.messages.ProjectBindingListener;
import org.sonarlint.intellij.messages.ProjectBindingListenerKt;
import org.sonarlint.intellij.notifications.IncludeResolvedIssueAction;
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications;
import org.sonarlint.intellij.ui.ReportPanel;
import org.sonarlint.intellij.ui.currentfile.CurrentFilePanel;
import org.sonarlint.intellij.ui.currentfile.filter.FilteredFindings;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.ui.ToolWindowConstants.CURRENT_FILE_TAB_TITLE;
import static org.sonarlint.intellij.ui.ToolWindowConstants.LOG_TAB_TITLE;
import static org.sonarlint.intellij.ui.ToolWindowConstants.REPORT_TAB_TITLE;
import static org.sonarlint.intellij.ui.ToolWindowConstants.TOOL_WINDOW_ID;
import static org.sonarlint.intellij.ui.UiUtils.runOnUiThread;
import static org.sonarlint.intellij.util.ThreadUtilsKt.runOnPooledThread;

@Service(Service.Level.PROJECT)
public final class SonarLintToolWindow implements ContentManagerListener, ProjectBindingListener {

  private final Project project;

  public SonarLintToolWindow(Project project) {
    this.project = project;
    project.getMessageBus().connect().subscribe(ProjectBindingListenerKt.getPROJECT_BINDING_TOPIC(), this);
  }

  /**
   * Must run in EDT
   */
  public void openReportTab(AnalysisResult analysisResult) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    this.<ReportPanel>openTab(REPORT_TAB_TITLE, panel -> panel.updateFindings(analysisResult));
  }

  public void openReportTab() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    this.openTab(REPORT_TAB_TITLE);
  }

  public void clearReportTab() {
    updateTab(REPORT_TAB_TITLE, ReportPanel::clear);
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
    this.<CurrentFilePanel>updateTab(CURRENT_FILE_TAB_TITLE, panel -> panel.allowResolvedFindings(isResolved));
  }

  /**
   * Must run in EDT
   */
  public void openCurrentFileTab() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    openTab(CURRENT_FILE_TAB_TITLE);
  }

  public void openOrCloseCurrentFileTab() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    var toolWindow = getToolWindow();
    if (toolWindow != null) {
      var contentManager = toolWindow.getContentManager();
      var content = contentManager.findContent(CURRENT_FILE_TAB_TITLE);
      if (content != null) {
        if (content.getComponent().isShowing()) {
          toolWindow.hide();
        } else {
          toolWindow.show(() -> contentManager.setSelectedContent(content));
        }
      }
    }
  }

  public void openLogTab() {
    openTab(LOG_TAB_TITLE);
  }

  public void refreshViews() {
    this.updateTab(CURRENT_FILE_TAB_TITLE, CurrentFilePanel::refreshView);
    this.updateTab(REPORT_TAB_TITLE, ReportPanel::refreshView);
  }

  public void setAnalysisReadyCurrentFile() {
    this.updateTab(CURRENT_FILE_TAB_TITLE, CurrentFilePanel::setAnalysisIsReady);
  }

  public FilteredFindings getDisplayedFindings() {
    var contentManager = getToolWindow().getContentManager();
    var content = contentManager.findContent(CURRENT_FILE_TAB_TITLE);
    var currentFilePanel = (CurrentFilePanel) content.getComponent();
    return currentFilePanel.getDisplayedFindings();
  }

  private void openTab(String name) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    var toolWindow = getToolWindow();
    if (toolWindow != null) {
      toolWindow.show(() -> selectTab(toolWindow, name));
    }
  }

  private ToolWindow getToolWindow() {
    var toolWindowManager = ToolWindowManager.getInstance(project);
    return toolWindowManager.getToolWindow(TOOL_WINDOW_ID);
  }

  private static void selectTab(ToolWindow toolWindow, String tabId) {
    var contentManager = toolWindow.getContentManager();
    var content = contentManager.findContent(tabId);
    if (content != null) {
      contentManager.setSelectedContent(content);
    }
  }

  public void updateCurrentFileTab(@Nullable VirtualFile selectedFile) {
    this.<CurrentFilePanel>updateTab(CURRENT_FILE_TAB_TITLE,
      panel -> runOnUiThread(project, () -> panel.update(selectedFile)));
  }

  public void showFindingDescription(Finding liveIssue) {
    openCurrentFileTab();
    selectTab(getToolWindow(), CURRENT_FILE_TAB_TITLE);
    var contentManager = getToolWindow().getContentManager();
    var content = contentManager.findContent(CURRENT_FILE_TAB_TITLE);
    var currentFilePanel = (CurrentFilePanel) content.getComponent();
    currentFilePanel.setSelectedFinding(liveIssue);
    currentFilePanel.selectRulesTab();
  }

  public void showFindingLocations(Finding liveIssue) {
    openCurrentFileTab();
    selectTab(getToolWindow(), CURRENT_FILE_TAB_TITLE);
    var contentManager = getToolWindow().getContentManager();
    var content = contentManager.findContent(CURRENT_FILE_TAB_TITLE);
    var currentFilePanel = (CurrentFilePanel) content.getComponent();
    currentFilePanel.setSelectedFinding(liveIssue);
    currentFilePanel.selectLocationsTab();
  }

  public void trySelectIssueForCodeFix(String findingKey) {
    var toolWindow = getToolWindow();
    if (toolWindow != null) {
      var contentManager = toolWindow.getContentManager();
      var content = contentManager.findContent(CURRENT_FILE_TAB_TITLE);
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
      var content = contentManager.findContent(CURRENT_FILE_TAB_TITLE);
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
      var contentManager = toolWindow.getContentManager();
      var content = contentManager.findContent(CURRENT_FILE_TAB_TITLE);
      if (content != null) {
        var currentFilePanel = (CurrentFilePanel) content.getComponent();
        var taint = currentFilePanel.getTaintFiltered(showFinding.getFindingKey());

        if (taint == null && currentFilePanel.doesTaintExist(showFinding.getFindingKey())) {
          getService(project, SonarLintProjectNotifications.class).notifyUnableToOpenFinding(
            "The taint vulnerability could not be opened by SonarQube for IDE due to the applied filters",
            new IncludeResolvedIssueAction()
          );
        }

        currentFilePanel.trySelectFilteredTaint(taint, showFinding);
      }
    }
  }

  public void trySelectTaintForCodeFix(String findingKey) {
    var toolWindow = getToolWindow();
    if (toolWindow != null) {
      var contentManager = toolWindow.getContentManager();
      var content = contentManager.findContent(CURRENT_FILE_TAB_TITLE);
      if (content != null) {
        var currentFilePanel = (CurrentFilePanel) content.getComponent();
        var taint = currentFilePanel.getTaintFiltered(findingKey);

        if (taint == null) {
          if (currentFilePanel.doesTaintExist(findingKey)) {
            getService(project, SonarLintProjectNotifications.class).notifyUnableToOpenFinding(
              "The taint vulnerability could not be opened by SonarQube for IDE due to the applied filters",
              new IncludeResolvedIssueAction()
            );
          } else {
            getService(project, SonarLintProjectNotifications.class).notifyUnableToOpenFinding(
              "The taint vulnerability was not found",
              new IncludeResolvedIssueAction()
            );
          }
        } else {
          currentFilePanel.trySelectTaintForCodeFix(taint);
        }
      }
    }
  }

  public <T extends Finding> void trySelectSecurityHotspot(ShowFinding<T> showFinding) {
    var toolWindow = getToolWindow();
    if (toolWindow != null) {
      var contentManager = toolWindow.getContentManager();
      var content = contentManager.findContent(CURRENT_FILE_TAB_TITLE);
      if (content != null) {
        var currentFilePanel = (CurrentFilePanel) content.getComponent();
        var hotspot = currentFilePanel.getHotspotFiltered(showFinding.getFindingKey());

        if (hotspot == null && currentFilePanel.doesHotspotExist(showFinding.getFindingKey())) {
          getService(project, SonarLintProjectNotifications.class).notifyUnableToOpenFinding(
            "The security hotspot could not be opened by SonarQube for IDE due to the applied filters",
            new IncludeResolvedIssueAction()
          );
        }

        currentFilePanel.trySelectFilteredHotspot(hotspot, showFinding);
      }
    }
  }

  public void bringToFront() {
    var toolWindow = getToolWindow();
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
  public void bindingChanged() {
    runOnPooledThread(project, this::refreshViews);
  }

}
