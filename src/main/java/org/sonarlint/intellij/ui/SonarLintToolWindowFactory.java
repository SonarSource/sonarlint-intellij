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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.content.ContentManager;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.actions.SonarLintToolWindow;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.ui.vulnerabilities.TaintVulnerabilitiesPanel;

import static org.sonarlint.intellij.actions.SonarLintToolWindow.buildVulnerabilitiesTabName;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

/**
 * Factory of SonarLint tool window.
 * Nothing can be injected as it runs in the root pico container.
 */
public class SonarLintToolWindowFactory implements ToolWindowFactory {
  public static final String TOOL_WINDOW_ID = "SonarLint";
  public static final String LOG_TAB_TITLE = "Log";
  public static final String CURRENT_FILE_TAB_TITLE = "Current file";
  public static final String REPORT_TAB_TITLE = "Report";
  public static final String TAINT_VULNERABILITIES_TAB_TITLE = "Taint vulnerabilities";

  @Override
  public void createToolWindowContent(Project project, final ToolWindow toolWindow) {
    var contentManager = toolWindow.getContentManager();
    addCurrentFileTab(project, contentManager);
    addReportTab(project, contentManager);
    if (SonarLintUtils.isTaintVulnerabilitiesEnabled()) {
      addTaintIssuesTab(project, contentManager);
    }
    addLogTab(project, toolWindow);
    toolWindow.setType(ToolWindowType.DOCKED, null);
    var sonarLintToolWindow = getService(project, SonarLintToolWindow.class);
    contentManager.addContentManagerListener(sonarLintToolWindow);
    var activeHotspot = sonarLintToolWindow.getActiveHotspot();
    if (activeHotspot != null) {
      sonarLintToolWindow.show(activeHotspot);
    }
  }

  public static JBSplitter createSplitter(Project project, JComponent parentComponent, Disposable parentDisposable, JComponent c1, JComponent c2, String proportionProperty,
    float defaultSplit) {
    var splitter = new OnePixelSplitter(splitVertically(project), proportionProperty, defaultSplit);
    splitter.setFirstComponent(c1);
    splitter.setSecondComponent(c2);
    splitter.setHonorComponentsMinimumSize(true);

    final var listener = new ToolWindowManagerListener() {
      @Override
      public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
        splitter.setOrientation(splitVertically(project));
        parentComponent.revalidate();
        parentComponent.repaint();
      }
    };
    project.getMessageBus().connect(parentDisposable).subscribe(ToolWindowManagerListener.TOPIC, listener);
    Disposer.register(parentDisposable, () -> {
      parentComponent.remove(splitter);
      splitter.dispose();
    });

    return splitter;
  }

  public static boolean splitVertically(Project project) {
    final var toolWindow = ToolWindowManager.getInstance(project).getToolWindow(SonarLintToolWindowFactory.TOOL_WINDOW_ID);
    var splitVertically = false;
    if (toolWindow != null) {
      final var anchor = toolWindow.getAnchor();
      splitVertically = anchor == ToolWindowAnchor.LEFT || anchor == ToolWindowAnchor.RIGHT;
    }
    return splitVertically;
  }

  private static void addCurrentFileTab(Project project, @NotNull ContentManager contentManager) {
    var issueManager = getService(project, IssueManager.class);
    var currentFilePanel = new CurrentFilePanel(project);
    var currentFileController = new CurrentFileController(project, issueManager, currentFilePanel);
    var currentFileContent = contentManager.getFactory()
      .createContent(
        currentFilePanel,
        CURRENT_FILE_TAB_TITLE,
        false);
    Disposer.register(currentFileContent, currentFileController);
    currentFileContent.setCloseable(false);
    contentManager.addDataProvider(currentFilePanel);
    contentManager.addContent(currentFileContent);
  }

  private static void addReportTab(Project project, @NotNull ContentManager contentManager) {
    var reportPanel = new ReportPanel(project);
    var reportContent = contentManager.getFactory()
      .createContent(
        reportPanel,
        REPORT_TAB_TITLE,
        false);
    reportContent.setCloseable(false);
    contentManager.addDataProvider(reportPanel);
    contentManager.addContent(reportContent);
  }

  private static void addTaintIssuesTab(Project project, @NotNull ContentManager contentManager) {
    var vulnerabilitiesPanel = new TaintVulnerabilitiesPanel(project);
    var analysisResultsContent = contentManager.getFactory()
      .createContent(
        vulnerabilitiesPanel,
        buildVulnerabilitiesTabName(0),
        false);
    analysisResultsContent.setCloseable(false);
    contentManager.addDataProvider(vulnerabilitiesPanel);
    contentManager.addContent(analysisResultsContent);
  }

  private static void addLogTab(Project project, ToolWindow toolWindow) {
    var logContent = toolWindow.getContentManager().getFactory()
      .createContent(
        new SonarLintLogPanel(toolWindow, project),
        LOG_TAB_TITLE,
        false);
    logContent.setCloseable(false);
    toolWindow.getContentManager().addContent(logContent);
  }
}
