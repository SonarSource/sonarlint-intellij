/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindow;
import javax.swing.Box;
import org.sonarlint.intellij.actions.ToolWindowVerboseModeAction;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.util.SonarLintActions;

public class SonarLintLogPanel extends SimpleToolWindowPanel {
  private static final String ID = "SonarQube for IDE";

  private final ToolWindow toolWindow;
  private final Project project;

  private transient ActionToolbar mainToolbar;

  public SonarLintLogPanel(ToolWindow toolWindow, Project project) {
    super(false, false);
    this.toolWindow = toolWindow;
    this.project = project;

    addLogActions();
    addToolbar();
    addConsole();
  }

  private void addToolbar() {
    var actionGroup = createActionGroup();
    mainToolbar = ActionManager.getInstance().createActionToolbar(ID, actionGroup, false);
    mainToolbar.setTargetComponent(this);
    var toolBarBox = Box.createHorizontalBox();
    toolBarBox.add(mainToolbar.getComponent());

    super.setToolbar(toolBarBox);
    mainToolbar.getComponent().setVisible(true);
  }

  private static ActionGroup createActionGroup() {
    var sonarLintActions = SonarLintActions.getInstance();
    var actionGroup = new DefaultActionGroup();
    actionGroup.add(sonarLintActions.configure());
    actionGroup.add(sonarLintActions.cleanConsole());
    return actionGroup;
  }

  private void addLogActions() {
    var group = new DefaultActionGroup();
    group.add(new ToolWindowVerboseModeAction());
    toolWindow.setAdditionalGearActions(group);
  }

  private void addConsole() {
    var consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
    SonarLintUtils.getService(project, SonarLintConsole.class).setConsoleView(consoleView);
    super.setContent(consoleView.getComponent());
  }
}
