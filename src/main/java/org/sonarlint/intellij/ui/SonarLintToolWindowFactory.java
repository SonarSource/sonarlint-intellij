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
package org.sonarlint.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
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
import org.sonarlint.intellij.ui.risks.DependencyRiskPanel;
import org.sonarlint.intellij.ui.vulnerabilities.TaintVulnerabilitiesPanel;

import static org.sonarlint.intellij.actions.SonarLintToolWindow.buildTabName;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.ui.UiUtils.runOnUiThread;

/**
 * Factory of SonarLint tool window.
 * Nothing can be injected as it runs in the root pico container.
 */
public class SonarLintToolWindowFactory implements ToolWindowFactory {
  public static final String TOOL_WINDOW_ID = "SonarQube for IDE";
  public static final String LOG_TAB_TITLE = "Log";
  public static final String CURRENT_FILE_TAB_TITLE = "Current File";
  public static final String REPORT_TAB_TITLE = "Report";
  public static final String TAINT_VULNERABILITIES_TAB_TITLE = "Taint Vulnerabilities";
  public static final String SECURITY_HOTSPOTS_TAB_TITLE = "Security Hotspots";
  public static final String DEPENDENCY_RISKS_TAB_TITLE = "Dependency Risks";
  public static final String HELP_AND_FEEDBACK_TAB_TITLE = "Help & Feedback";

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
      addDependencyRiskTab(project, contentManager);
      addLogTab(project, toolWindow);
      addHelpAndFeedbackTab(project, toolWindow);
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
    addTab(currentFilePanel, contentManager, CURRENT_FILE_TAB_TITLE);
  }

  private static void addReportTab(Project project, @NotNull ContentManager contentManager) {
    var reportPanel = new ReportPanel(project);
    addTab(reportPanel, contentManager, REPORT_TAB_TITLE);
  }

  private static void addTaintVulnerabilitiesTab(Project project, @NotNull ContentManager contentManager) {
    var taintVulnerabilitiesPanel = new TaintVulnerabilitiesPanel(project);
    addTab(taintVulnerabilitiesPanel, contentManager, buildTabName(0, SonarLintToolWindowFactory.TAINT_VULNERABILITIES_TAB_TITLE));
  }

  private static void addSecurityHotspotsTab(Project project, @NotNull ContentManager contentManager) {
    var securityHotspotsPanel = new SecurityHotspotsPanel(project);
    addTab(securityHotspotsPanel, contentManager, buildTabName(0, SECURITY_HOTSPOTS_TAB_TITLE));
  }

  private static void addDependencyRiskTab(Project project, ContentManager contentManager) {
    var dependencyRisksPanel = new DependencyRiskPanel(project);
    addTab(dependencyRisksPanel, contentManager, buildTabName(0, DEPENDENCY_RISKS_TAB_TITLE));
  }

  private static void addTab(SimpleToolWindowPanel panel, ContentManager contentManager, String title) {
    var securityHotspotsContent = contentManager.getFactory()
      .createContent(
        panel,
        title,
        false);
    securityHotspotsContent.setCloseable(false);
    contentManager.addDataProvider(panel);
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

  private static void addHelpAndFeedbackTab(Project project, ToolWindow toolWindow) {
    var helpContent = toolWindow.getContentManager().getFactory()
      .createContent(
        new SonarLintHelpAndFeedbackPanel(project),
        HELP_AND_FEEDBACK_TAB_TITLE,
        false);
    helpContent.setCloseable(false);
    toolWindow.getContentManager().addContent(helpContent);
  }

  public static ToolWindow getSonarLintToolWindow(Project project) {
    var toolWindowManager = ToolWindowManager.getInstance(project);
    return toolWindowManager.getToolWindow(SonarLintToolWindowFactory.TOOL_WINDOW_ID);
  }
}
