/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
import org.sonarlint.intellij.ui.vulnerabilities.TaintVulnerabilitiesPanel;

import static org.sonarlint.intellij.actions.SonarLintToolWindow.buildTabName;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.ui.UiUtils.runOnUiThread;

/**
 * Factory of SonarLint tool window.
 * Nothing can be injected as it runs in the root pico container.
 */
public class SonarLintToolWindowFactory implements ToolWindowFactory {
  public static final String TOOL_WINDOW_ID = "SonarLint";
  public static final String LOG_TAB_TITLE = "Log";
  public static final String CURRENT_FILE_TAB_TITLE = "Current File";
  public static final String REPORT_TAB_TITLE = "Report";
  public static final String TAINT_VULNERABILITIES_TAB_TITLE = "Taint Vulnerabilities";
  public static final String SECURITY_HOTSPOTS_TAB_TITLE = "Security Hotspots";

  @Override
  public void createToolWindowContent(Project project, final ToolWindow toolWindow) {
    runOnUiThread(project, () -> {
      var contentManager = toolWindow.getContentManager();
      addCurrentFileTab(project, contentManager);
      addReportTab(project, contentManager);
      var sonarLintToolWindow = getService(project, SonarLintToolWindow.class);
      addSecurityHotspotsTab(project, contentManager);
      if (SonarLintUtils.isTaintVulnerabilitiesEnabled()) {
        addTaintVulnerabilitiesTab(project, contentManager);
      }
      addLogTab(project, toolWindow);
      toolWindow.setType(ToolWindowType.DOCKED, null);
      contentManager.addContentManagerListener(sonarLintToolWindow);
    });
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
    var currentFilePanel = new CurrentFilePanel(project);
    var currentFileContent = contentManager.getFactory()
      .createContent(
        currentFilePanel,
        CURRENT_FILE_TAB_TITLE,
        false);
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

  private static void addTaintVulnerabilitiesTab(Project project, @NotNull ContentManager contentManager) {
    var vulnerabilitiesPanel = new TaintVulnerabilitiesPanel(project);
    var taintVulnerabilitiesContent = contentManager.getFactory()
      .createContent(
        vulnerabilitiesPanel,
        buildTabName(0, SonarLintToolWindowFactory.TAINT_VULNERABILITIES_TAB_TITLE),
        false);
    taintVulnerabilitiesContent.setCloseable(false);
    contentManager.addDataProvider(vulnerabilitiesPanel);
    contentManager.addContent(taintVulnerabilitiesContent);
  }

  private static void addSecurityHotspotsTab(Project project, @NotNull ContentManager contentManager) {
    var hotspotsPanel = new SecurityHotspotsPanel(project);
    var securityHotspotsContent = contentManager.getFactory()
      .createContent(
        hotspotsPanel,
        buildTabName(0, SECURITY_HOTSPOTS_TAB_TITLE),
        false);
    securityHotspotsContent.setCloseable(false);
    contentManager.addDataProvider(hotspotsPanel);
    contentManager.addContent(securityHotspotsContent);
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

  public static ToolWindow getSonarLintToolWindow(Project project) {
    var toolWindowManager = ToolWindowManager.getInstance(project);
    return toolWindowManager.getToolWindow(SonarLintToolWindowFactory.TOOL_WINDOW_ID);
  }
}
