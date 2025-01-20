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
package org.sonarlint.intellij;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public class OpenWelcomePageOnceOneProjectOpened implements StartupActivity {

  public static final String HAS_WALKTHROUGH_RUN_ONCE = "hasWalkthroughRunOnce";

  @Override
  public void runActivity(@NotNull Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    var properties = PropertiesComponent.getInstance();

    if (!properties.getBoolean(HAS_WALKTHROUGH_RUN_ONCE, false)) {
      properties.setValue(HAS_WALKTHROUGH_RUN_ONCE, true);
      openWelcomePage(project);
    }
  }

  private static void openWelcomePage(Project project) {
    var toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Welcome to SonarQube for IDE");

    if (toolWindow == null) {
      return;
    }

    toolWindow.show();
  }
}
