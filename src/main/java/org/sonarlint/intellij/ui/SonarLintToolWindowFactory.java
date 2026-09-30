/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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

import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import org.sonarlint.intellij.actions.SonarLintToolWindow;
import org.sonarlint.intellij.actions.ToolWindowVerboseModeAction;
import org.sonarlint.intellij.ai.AiIntegrationsController;
import org.sonarlint.intellij.ai.AiIntegrationsPanel;
import org.sonarlint.intellij.ui.currentfile.CurrentFilePanel;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;
import static org.sonarlint.intellij.ui.ToolWindowConstants.HELP_AND_FEEDBACK_TAB_TITLE;
import static org.sonarlint.intellij.ui.ToolWindowConstants.AI_INTEGRATIONS_TAB_TITLE;
import static org.sonarlint.intellij.ui.ToolWindowConstants.LOG_TAB_TITLE;
import static org.sonarlint.intellij.ui.UiUtils.runOnUiThread;

/**
 * Factory of SonarQube for IDE tool window.
 * Nothing can be injected as it runs in the root pico container.
 */
public class SonarLintToolWindowFactory implements ToolWindowFactory {

  @Override
  public void createToolWindowContent(Project project, final ToolWindow toolWindow) {
    runOnUiThread(project, () -> {
      configureAdditionalGearActions(toolWindow);
      var contentManager = toolWindow.getContentManager();
      var findingsContent = addCurrentFileTab(project, contentManager);
      var sonarLintToolWindow = getService(project, SonarLintToolWindow.class);
      addAiIntegrationsTab(project, contentManager);
      addLogTab(project, toolWindow);
      addHelpAndFeedbackTab(project, toolWindow);
      contentManager.setSelectedContent(findingsContent);
      toolWindow.setType(ToolWindowType.DOCKED, null);
      contentManager.addContentManagerListener(sonarLintToolWindow);
    });
  }

  private static void configureAdditionalGearActions(ToolWindow toolWindow) {
    var gearActions = new DefaultActionGroup();
    gearActions.add(new ToolWindowVerboseModeAction());
    toolWindow.setAdditionalGearActions(gearActions);
  }

  private static Content addCurrentFileTab(Project project, ContentManager contentManager) {
    var currentFilePanel = new CurrentFilePanel(project);
    return addCurrentFileTab(currentFilePanel, contentManager);
  }

  private static Content addCurrentFileTab(SimpleToolWindowPanel panel, ContentManager contentManager) {
    var content = contentManager.getFactory()
      .createContent(
        panel,
        ToolWindowConstants.CURRENT_FILE_TAB_TITLE,
        false);
    content.setCloseable(false);
    contentManager.addDataProvider(panel);
    contentManager.addContent(content);
    return content;
  }

  private static void addAiIntegrationsTab(Project project, ContentManager contentManager) {
    var panel = new AiIntegrationsPanel();
    var controller = new AiIntegrationsController(project, panel);
    Disposer.register(project, controller);
    var content = contentManager.getFactory().createContent(panel, AI_INTEGRATIONS_TAB_TITLE, false);
    content.setCloseable(false);
    contentManager.addContent(content);
    contentManager.addContentManagerListener(new ContentManagerListener() {
      @Override
      public void selectionChanged(ContentManagerEvent event) {
        if (event.getContent() == content && event.getOperation() == ContentManagerEvent.ContentOperation.add) {
          controller.loadInitially();
        }
      }
    });
  }

  private static void addLogTab(Project project, ToolWindow toolWindow) {
    var logContent = toolWindow.getContentManager().getFactory()
      .createContent(
        new SonarLintLogPanel(project),
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

}
