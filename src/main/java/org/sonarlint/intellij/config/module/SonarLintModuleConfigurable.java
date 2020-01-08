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
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import javax.swing.JComponent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;

public class SonarLintModuleConfigurable implements Configurable, Configurable.NoMargin, Configurable.NoScroll {
  private final Module module;
  private final SonarLintModuleSettings moduleSettings;
  private final SonarLintProjectSettings projectSettings;
  private SonarLintModulePanel panel;

  public SonarLintModuleConfigurable(Module module, SonarLintModuleSettings moduleSettings, SonarLintProjectSettings projectSettings) {
    this.module = module;
    this.moduleSettings = moduleSettings;
    this.projectSettings = projectSettings;
  }

  @Nls(capitalization = Nls.Capitalization.Title)
  @Override
  public String getDisplayName() {
    return "SonarLint";
  }

  @Nullable @Override
  public JComponent createComponent() {
    if (panel == null) {
      panel = new SonarLintModulePanel(module);
    }
    return panel.getRootPanel();
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void reset() {
    if (panel != null) {
      panel.load(moduleSettings, projectSettings);
    }
  }

  @javax.annotation.Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  public void apply() throws ConfigurationException {

  }

  @Override
  public void disposeUIResources() {
    // nothing to do
  }
}
