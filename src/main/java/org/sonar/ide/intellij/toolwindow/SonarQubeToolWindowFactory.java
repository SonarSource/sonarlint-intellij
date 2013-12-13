/*
 * SonarQube IntelliJ
 * Copyright (C) 2013 SonarSource
 * dev@sonar.codehaus.org
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
package org.sonar.ide.intellij.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import org.sonar.ide.intellij.config.ProjectSettings;

public class SonarQubeToolWindowFactory implements ToolWindowFactory {

  public static final String ID = "SonarQube";

  @Override
  public void createToolWindowContent(Project project, ToolWindow toolWindow) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new EnableVerboseModeAction());

    ((ToolWindowEx) toolWindow).setAdditionalGearActions(group);
  }


  private static class EnableVerboseModeAction extends ToggleAction implements DumbAware {
    public EnableVerboseModeAction() {
      super("Verbose output", "Enable verbose output for local analysis",
          AllIcons.General.Debug);
    }

    public boolean isSelected(AnActionEvent event) {
      Project p = event.getProject();
      return p != null &&  p.getComponent(ProjectSettings.class).isVerboseEnabled();
    }

    public void setSelected(AnActionEvent event, boolean flag) {
      Project p = event.getProject();
      if (p != null) {
        p.getComponent(ProjectSettings.class).setVerboseEnabled(flag);
      }
    }
  }
}
