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
package org.sonarlint.intellij.config.module;

import com.intellij.openapi.module.Module;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;

public class SonarLintModulePanel {
  private final Module module;
  private JPanel rootPanel;
  private JTextField sqPathText;
  private JPanel group;
  private JTextField projectKeyText;
  private JTextField idePathText;

  public SonarLintModulePanel(Module module) {
    this.module = module;
  }

  public JPanel getRootPanel() {
    return rootPanel;
  }

  public void load(SonarLintModuleSettings settings, SonarLintProjectSettings projectSettings) {
    if (projectSettings.getProjectKey() != null) {
      idePathText.setText(settings.getIdePathPrefix());
      sqPathText.setText(settings.getSqPathPrefix());
      projectKeyText.setText(projectSettings.getProjectKey());
    } else {
      idePathText.setText("");
      sqPathText.setText("");
      projectKeyText.setText("<No binding configured>");
    }
  }
}
