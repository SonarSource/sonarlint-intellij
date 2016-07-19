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

import com.intellij.ide.DataManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;

import javax.swing.JComponent;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.core.SonarLintProjectNotifications;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;
import org.sonarlint.intellij.util.SonarLintUtils;

/**
 * Coordinates creation of models and visual components from persisted settings.
 * Transforms objects as needed and keeps track of changes.
 */
public class SonarLintProjectConfigurable implements Configurable, Configurable.NoMargin, Configurable.NoScroll {

  private final Project project;
  private final SonarLintProjectSettings projectSettings;
  private final MessageBusConnection bus;

  private SonarLintProjectSettingsPanel panel;

  public SonarLintProjectConfigurable(Project project) {
    this.project = project;
    this.projectSettings = project.getComponent(SonarLintProjectSettings.class);
    this.bus = ApplicationManager.getApplication().getMessageBus().connect();
    this.bus.subscribe(GlobalConfigurationListener.SONARLINT_GLOBAL_CONFIG_TOPIC, newServerList -> {
      if (panel != null) {
        panel.serversChanged(newServerList);
      }
    });
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
      panel = new SonarLintProjectSettingsPanel(project);
    }
    return panel.getRootPane();
  }

  @Override
  public boolean isModified() {
    if (panel != null) {
      return panel.isModified(projectSettings);
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    if (panel != null) {
      panel.save(projectSettings);
    }
  }

  @Override
  public void reset() {
    if (panel != null) {
      SonarLintGlobalSettings globalSettings = null;

      // try get the global settings that are currently being configured in the configurable, if it is open
      Settings allSettings = Settings.KEY.getData(DataManager.getInstance().getDataContextFromFocus().getResult());
      if (allSettings != null) {
        final SonarLintGlobalConfigurable globalConfigurable = allSettings.find(SonarLintGlobalConfigurable.class);
        if (globalConfigurable != null) {
          globalSettings = globalConfigurable.getCurrentSettings();
        }
      }

      // get saved settings if needed
      if (globalSettings == null) {
        globalSettings = SonarLintUtils.get(SonarLintGlobalSettings.class);
      }
      panel.load(globalSettings.getSonarQubeServers(), projectSettings);
    }
  }

  @Override
  public void disposeUIResources() {
    SonarLintProjectNotifications.get(project).reset();
    bus.disconnect();
    if (panel != null) {
      panel.dispose();
      panel = null;
    }
  }
}
