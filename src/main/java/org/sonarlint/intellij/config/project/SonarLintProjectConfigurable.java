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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import javax.swing.JComponent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.core.SonarLintProjectNotifications;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;

/**
 * Coordinates creation of models and visual components from persisted settings.
 * Transforms objects as needed and keeps track of changes.
 */
public class SonarLintProjectConfigurable implements Configurable, Configurable.NoMargin {

  private final Project project;
  private final SonarLintProjectSettings projectSettings;
  private final MessageBusConnection bus;

  private SonarLintProjectSettingsPanel panel;

  public SonarLintProjectConfigurable(Project p) {
    this.project = p;
    this.projectSettings = project.getComponent(SonarLintProjectSettings.class);
    this.bus = ApplicationManager.getApplication().getMessageBus().connect();
    this.bus.subscribe(GlobalConfigurationListener.SONARLINT_GLOBAL_CONFIG_TOPIC, new GlobalConfigurationListener() {
      @Override public void changed() {
        reset();
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
      // global settings might have been changed
      SonarLintGlobalSettings globalSettings = ApplicationManager.getApplication().getComponent(SonarLintGlobalSettings.class);
      panel.load(globalSettings, projectSettings);
    }
  }

  @Override
  public void disposeUIResources() {
    SonarLintProjectNotifications.get(project).reset();
    bus.disconnect();
    panel = null;
  }
}
