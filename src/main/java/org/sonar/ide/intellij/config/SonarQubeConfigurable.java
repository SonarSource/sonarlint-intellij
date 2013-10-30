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
package org.sonar.ide.intellij.config;

import com.intellij.openapi.options.Configurable;
import org.sonar.ide.intellij.util.SonarQubeBundle;

import javax.swing.Icon;

public class SonarQubeConfigurable implements Configurable {

  private SonarQubeSettingsForm settingsForm;

  @org.jetbrains.annotations.Nls
  @Override
  public String getDisplayName() {
    return SonarQubeBundle.message("sonarqube.sonarqube");
  }

  @org.jetbrains.annotations.Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @org.jetbrains.annotations.Nullable
  @Override
  public javax.swing.JComponent createComponent() {
    if (settingsForm == null) {
      settingsForm = new SonarQubeSettingsForm();
      settingsForm.setServers(SonarQubeSettings.getInstance().getServers());
    }
    return settingsForm.getFormComponent();
  }

  @Override
  public boolean isModified() {
    if (settingsForm != null) {
      return settingsForm.isModified();
    }
    return false;

  }

  @Override
  public void apply() throws com.intellij.openapi.options.ConfigurationException {
    if (settingsForm != null) {
      SonarQubeSettings.getInstance().getServers().clear();
      SonarQubeSettings.getInstance().getServers().addAll(settingsForm.getServers());
    }

  }

  @Override
  public void reset() {
    if (settingsForm != null) {
      settingsForm.setServers(SonarQubeSettings.getInstance().getServers());
    }

  }

  @Override
  public void disposeUIResources() {
    settingsForm = null;
  }

  public Icon getIcon() {
    return null;
  }

}
