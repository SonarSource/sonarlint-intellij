/**
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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
package org.sonarlint.intellij.config.project;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import javax.swing.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

/**
 * Coordinates creation of models and visual components from persisted settings.
 * Transforms objects as needed and keeps track of changes.
 */
public class SonarLintProjectConfigurable implements Configurable {

  private final Project project;
  private final SonarLintProjectSettings projectSettings;

  private SonarLintProjectSettingsPanel panel;

  public SonarLintProjectConfigurable(Project p) {
    this.project = p;
    this.projectSettings = project.getComponent(SonarLintProjectSettings.class);
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "SonarLint";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    if (panel == null) {
      panel = new SonarLintProjectSettingsPanel(projectSettings.getAdditionalProperties());
    }
    return panel.getRootPane();
  }

  @Override
  public boolean isModified() {
    return !projectSettings.getAdditionalProperties().equals(panel.getProperties());
  }

  @Override
  public void apply() throws ConfigurationException {
    projectSettings.setAdditionalProperties(panel.getProperties());
  }

  @Override
  public void reset() {
    panel.setProperties(projectSettings.getAdditionalProperties());
  }

  @Override
  public void disposeUIResources() {
    panel = null;
  }
}
