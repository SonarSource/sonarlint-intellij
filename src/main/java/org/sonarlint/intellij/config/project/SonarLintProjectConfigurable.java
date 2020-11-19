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
package org.sonarlint.intellij.config.project;

import com.intellij.ide.DataManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBusConnection;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import org.jetbrains.annotations.Nls;
import org.jetbrains.concurrency.Promise;
import org.sonarlint.intellij.config.global.SonarLintGlobalConfigurable;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.core.ProjectBindingManager;
import org.sonarlint.intellij.core.SonarLintProjectNotifications;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.messages.GlobalConfigurationListener;
import org.sonarlint.intellij.messages.ProjectConfigurationListener;
import org.sonarlint.intellij.tasks.ConnectionUpdateTask;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;

import static org.sonarlint.intellij.config.Settings.getGlobalSettings;
import static org.sonarlint.intellij.config.Settings.getSettingsFor;

/**
 * Coordinates creation of models and visual components from persisted settings.
 * Transforms objects as needed and keeps track of changes.
 */
public class SonarLintProjectConfigurable implements Configurable, Configurable.NoMargin, Configurable.NoScroll {

  private final Project project;
  private final MessageBusConnection busConnection;

  private SonarLintProjectSettingsPanel panel;

  public SonarLintProjectConfigurable(Project project) {
    this.project = project;
    this.busConnection = ApplicationManager.getApplication().getMessageBus().connect(project);
    this.busConnection.subscribe(GlobalConfigurationListener.TOPIC, new GlobalConfigurationListener.Adapter() {
      @Override
      public void changed(List<ServerConnection> newServerList) {
        if (panel != null) {
          panel.serversChanged(newServerList);
        }
      }
    });
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Project Settings";
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
    return panel != null && panel.isModified(getSettingsFor(project));
  }

  @Override
  public void apply() {
    if (panel != null) {
      SonarLintProjectSettings projectSettings = getSettingsFor(project);
      boolean exclusionsModified = panel.isExclusionsModified(projectSettings);
      panel.save(projectSettings);
      onSave(exclusionsModified);
    }
  }

  /**
   * When we save the binding, we need to:
   * - Send a message for listeners interested in it
   * - If we are bound to a project, update it (even if we detected no changes)
   * - Clear all issues and submit an analysis on all open files
   */
  private void onSave(boolean exclusionsModified) {
    SonarLintProjectNotifications.get(project).reset();
    ProjectConfigurationListener projectListener = project.getMessageBus().syncPublisher(ProjectConfigurationListener.TOPIC);
    SonarLintProjectSettings projectSettings = getSettingsFor(project);
    if (projectSettings.isBindingEnabled() && projectSettings.getProjectKey() != null && projectSettings.getConnectionName() != null) {
      ProjectBindingManager bindingManager = SonarLintUtils.getService(project, ProjectBindingManager.class);

      try {
        ServerConnection server = bindingManager.getServerConnection();
        ConnectedSonarLintEngine engine = bindingManager.getConnectedEngineSkipChecks();
        String projectKey = projectSettings.getProjectKey();

        ConnectionUpdateTask task = new ConnectionUpdateTask(engine, server, Collections.singletonMap(projectKey, Collections.singletonList(project)), true);
        ProgressManager.getInstance().run(task.asModal());
      } catch (InvalidBindingException e) {
        // nothing to do, SonarLintEngineManager should have already shown a warning
      }
    }
    projectListener.changed(projectSettings);

    if (exclusionsModified) {
      SonarLintUtils.getService(project, SonarLintSubmitter.class).submitOpenFilesAuto(TriggerType.CONFIG_CHANGE);
    }
  }

  @Override
  public void reset() {
    if (panel == null) {
      return;
    }

    getServersFromApplicationConfigurable()
      .onProcessed(sonarQubeServers ->
        panel.load(sonarQubeServers != null ? sonarQubeServers : getGlobalSettings().getServerConnections(), getSettingsFor(project))
      );
  }

  private static Promise<List<ServerConnection>> getServersFromApplicationConfigurable() {
    return DataManager.getInstance().getDataContextFromFocusAsync()
      .then(dataContext -> {
        Settings allSettings = Settings.KEY.getData(dataContext);
        if (allSettings != null) {
          final SonarLintGlobalConfigurable globalConfigurable = allSettings.find(SonarLintGlobalConfigurable.class);
          if (globalConfigurable != null) {
            return globalConfigurable.getCurrentConnections();
          }
        }
        return null;
      });
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
