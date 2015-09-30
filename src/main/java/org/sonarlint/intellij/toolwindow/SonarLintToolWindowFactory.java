/**
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
 * sonarqube@googlegroups.com
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
package org.sonarlint.intellij.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import org.sonarlint.intellij.config.SonarLintProjectSettings;

public class SonarLintToolWindowFactory implements ToolWindowFactory {

  public static final String ID = "SonarLint";

  @Override
  public void createToolWindowContent(Project project, ToolWindow toolWindow) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new EnableVerboseModeAction());

    ((ToolWindowEx) toolWindow).setAdditionalGearActions(group);
  }

  private static class EnableVerboseModeAction extends ToggleAction implements DumbAware {
    public EnableVerboseModeAction() {
      super("Verbose output", "Enable verbose output for SonarLint analysis",
        AllIcons.General.Debug);
    }

    @Override
    public boolean isSelected(AnActionEvent event) {
      Project p = event.getProject();
      return p != null && p.getComponent(SonarLintProjectSettings.class).isVerboseEnabled();
    }

    @Override
    public void setSelected(AnActionEvent event, boolean flag) {
      Project p = event.getProject();
      if (p != null) {
        p.getComponent(SonarLintProjectSettings.class).setVerboseEnabled(flag);
      }
    }
  }
}
