/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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

import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.tools.SimpleActionGroup;
import com.intellij.util.messages.MessageBusConnection;
import javax.swing.Box;
import org.sonarlint.intellij.actions.ToolWindowLogAnalysisAction;
import org.sonarlint.intellij.actions.ToolWindowVerboseModeAction;
import org.sonarlint.intellij.messages.StatusListener;
import org.sonarlint.intellij.util.SonarLintActions;
import org.sonarlint.intellij.util.SonarLintUtils;

public class SonarLintLogPanel extends SimpleToolWindowPanel {
  private static final String ID = "SonarLint";

  private final ToolWindow toolWindow;
  private final Project project;

  private ActionToolbar mainToolbar;

  public SonarLintLogPanel(ToolWindow toolWindow, Project project) {
    super(false, true);
    this.toolWindow = toolWindow;
    this.project = project;

    addLogActions();
    addToolbar();
    addConsole();

    MessageBusConnection busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(StatusListener.SONARLINT_STATUS_TOPIC, newStatus ->
      ApplicationManager.getApplication().invokeLater(mainToolbar::updateActionsImmediately));
  }

  private void addToolbar() {
    ActionGroup actionGroup = createActionGroup();
    mainToolbar = ActionManager.getInstance().createActionToolbar(ID, actionGroup, false);
    mainToolbar.setTargetComponent(this);
    Box toolBarBox = Box.createHorizontalBox();
    toolBarBox.add(mainToolbar.getComponent());

    super.setToolbar(toolBarBox);
    mainToolbar.getComponent().setVisible(true);
  }

  private static ActionGroup createActionGroup() {
    SonarLintActions sonarLintActions = SonarLintActions.getInstance();
    SimpleActionGroup actionGroup = new SimpleActionGroup();
    actionGroup.add(sonarLintActions.configure());
    actionGroup.add(sonarLintActions.showAnalyzers());
    actionGroup.add(sonarLintActions.cleanConsole());
    return actionGroup;
  }

  private void addLogActions() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new ToolWindowLogAnalysisAction());
    group.add(new ToolWindowVerboseModeAction());
    ((ToolWindowEx) toolWindow).setAdditionalGearActions(group);
  }

  private void addConsole() {
    ConsoleView consoleView = SonarLintUtils.get(project, SonarLintConsole.class).getConsoleView();
    super.setContent(consoleView.getComponent());
  }
}
