/*
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
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import org.jetbrains.annotations.Nls;
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.core.SonarLintProjectNotifications;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;
import org.sonarlint.intellij.tasks.ServerUpdateTask;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;

/**
 * Coordinates creation of models and visual components from persisted settings.
 * Transforms objects as needed and keeps track of changes.
 */
public class SonarLintProjectConfigurable implements Configurable, Configurable.NoMargin, Configurable.NoScroll {

  private final Project project;
  private final SonarLintProjectSettings projectSettings;
  private final MessageBusConnection busConnection;

  private SonarLintProjectSettingsPanel panel;

  public SonarLintProjectConfigurable(Project project) {
    this.project = project;
    this.projectSettings = project.getComponent(SonarLintProjectSettings.class);
    this.busConnection = ApplicationManager.getApplication().getMessageBus().connect(project);
    this.busConnection.subscribe(GlobalConfigurationListener.TOPIC, new GlobalConfigurationListener.Adapter() {
      @Override public void changed(List<SonarQubeServer> newServerList) {
        if (panel != null) {
          panel.serversChanged(newServerList);
        }
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
    return panel != null && panel.isModified(projectSettings);
  }

  @Override
  public void apply() throws ConfigurationException {
    if (panel != null) {
      panel.save(projectSettings);
      onSave();
    }
  }

  /**
   * When we save the binding, we need to:
   * - Send a message for listeners interested in it
   * - If we are bound to a module, update it (even if we detected no changes)
   * - Clear all issues and submit an analysis on all open files
   */
  private void onSave() {
    ProjectConfigurationListener projectListener = project.getMessageBus().syncPublisher(ProjectConfigurationListener.TOPIC);

    if (projectSettings.isBindingEnabled() && projectSettings.getProjectKey() != null && projectSettings.getServerId() != null) {
      ProjectBindingManager bindingManager = SonarLintUtils.get(project, ProjectBindingManager.class);

      SonarQubeServer server = bindingManager.getSonarQubeServer();
      ConnectedSonarLintEngine engine = bindingManager.getConnectedEngineSkipChecks();
      String moduleKey = projectSettings.getProjectKey();

      ServerUpdateTask task = new ServerUpdateTask(engine, server, Collections.singletonMap(moduleKey, Collections.singletonList(project)), true);
      ProgressManager.getInstance().run(task.asModal());
    }
    projectListener.changed(projectSettings);
  }

  @Override
  public void reset() {
    if (panel == null) {
      return;
    }

    List<SonarQubeServer> currentServers = null;

    // try get the global settings that are currently being configured in the configurable, if it is open
    DataContext ctx = DataManager.getInstance().getDataContextFromFocus().getResult();
    if (ctx != null) {
      Settings allSettings = Settings.KEY.getData(ctx);
      if (allSettings != null) {
        final SonarLintGlobalConfigurable globalConfigurable = allSettings.find(SonarLintGlobalConfigurable.class);
        if (globalConfigurable != null) {
          currentServers = globalConfigurable.getCurrentSettings();
        }
      }
    }

    // get saved settings if needed
    if (currentServers == null) {
      currentServers = SonarLintUtils.get(SonarLintGlobalSettings.class).getSonarQubeServers();
    }
    panel.load(currentServers, projectSettings);
  }

  @Override
  public void disposeUIResources() {
    SonarLintProjectNotifications.get(project).reset();
    busConnection.disconnect();
    if (panel != null) {
      panel.dispose();
      panel = null;
    }
  }
}
