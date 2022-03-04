/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
package org.sonarlint.intellij.core;

import com.google.common.base.Preconditions;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.serviceContainer.NonInjectable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonar.api.utils.log.Loggers;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarlint.intellij.notifications.AnalysisRequirementNotifications;
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications;
import org.sonarsource.sonarlint.core.client.api.common.SonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

import static com.intellij.openapi.progress.PerformInBackgroundOption.ALWAYS_BACKGROUND;
import static org.sonarlint.intellij.config.Settings.getGlobalSettings;

public class SonarLintEngineManager implements Disposable {
  private final Map<String, ConnectedSonarLintEngine> connectedEngines = new HashMap<>();
  private final SonarLintEngineFactory factory;
  private StandaloneSonarLintEngine standalone;

  public SonarLintEngineManager() {
    this(new SonarLintEngineFactory());
  }

  @NonInjectable
  SonarLintEngineManager(SonarLintEngineFactory factory) {
    this.factory = factory;
  }

  private static void doInBackground(String title, Runnable r) {
    ProgressManager.getInstance()
      .run(new Task.Backgroundable(null, title, false, ALWAYS_BACKGROUND) {

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setText2(title);
          r.run();
        }
      });
  }

  private static void checkConnectedEngineStatus(ConnectedSonarLintEngine engine, SonarLintProjectNotifications notifications, String serverId, String projectKey)
    throws InvalidBindingException {
    // Check if engine's global storage is OK
    ConnectedSonarLintEngine.State state = engine.getState();
    if (state != ConnectedSonarLintEngine.State.UPDATED) {
      if (state == ConnectedSonarLintEngine.State.NEED_UPDATE) {
        notifications.notifyServerStorageNeedsUpdate(serverId);
      } else if (state == ConnectedSonarLintEngine.State.NEVER_UPDATED) {
        notifications.notifyServerNeverUpdated(serverId);
      }
      throw new InvalidBindingException("Connection local storage is not updated: '" + serverId + "'");
    }

    // Check if project's storage is OK. Global storage was updated and all project's binding that were open too,
    // but we might have now opened a new project with a different binding.
    ProjectStorageStatus moduleStorageStatus = engine.getProjectStorageStatus(projectKey);

    if (moduleStorageStatus == null) {
      notifications.notifyModuleInvalid();
      throw new InvalidBindingException("Project is bound to a project that doesn't exist: " + projectKey);
    } else if (moduleStorageStatus.isStale()) {
      notifications.notifyModuleStale();
      throw new InvalidBindingException("Stale project's storage: " + projectKey);
    }
  }

  /**
   * Immediately removes and asynchronously stops all {@link ConnectedSonarLintEngine} corresponding to server IDs that were removed.
   */
  public synchronized void stopAllDeletedConnectedEnginesAsync() {
    Iterator<Map.Entry<String, ConnectedSonarLintEngine>> it = connectedEngines.entrySet().iterator();
    Set<String> configuredStorageIds = getServerNames();
    while (it.hasNext()) {
      Map.Entry<String, ConnectedSonarLintEngine> e = it.next();
      if (!configuredStorageIds.contains(e.getKey())) {
        doInBackground("Stop SonarLint engine '" + e.getKey() + "'", () -> e.getValue().stop(false));
        it.remove();
      }
    }
  }

  public synchronized void stopAllEngines(boolean async) {
    AnalysisRequirementNotifications.resetCachedMessages();
    for (Map.Entry<String, ConnectedSonarLintEngine> e : connectedEngines.entrySet()) {
      if (async) {
        String key = e.getKey();
        ConnectedSonarLintEngine engine = e.getValue();
        doInBackground("Stop SonarLint engine '" + key + "'", () -> engine.stop(false));
      } else {
        e.getValue().stop(false);
      }
    }
    connectedEngines.clear();
    if (standalone != null) {
      if (async) {
        StandaloneSonarLintEngine standaloneCopy = this.standalone;
        doInBackground("Stop standalone SonarLint engine", standaloneCopy::stop);
      } else {
        standalone.stop();
      }
      standalone = null;
    }
  }

  public synchronized ConnectedSonarLintEngine getConnectedEngine(String connectionId) {
    return connectedEngines.computeIfAbsent(connectionId, factory::createEngine);
  }

  public synchronized StandaloneSonarLintEngine getStandaloneEngine() {
    if (standalone == null) {
      standalone = factory.createEngine();
    }
    return standalone;
  }

  public ConnectedSonarLintEngine getConnectedEngine(SonarLintProjectNotifications notifications, String serverId, String projectKey) throws InvalidBindingException {
    Preconditions.checkNotNull(notifications, "notifications");
    Preconditions.checkNotNull(serverId, "serverId");
    Preconditions.checkNotNull(projectKey, "projectKey");

    Set<String> configuredStorageIds = getServerNames();
    if (!configuredStorageIds.contains(serverId)) {
      notifications.notifyConnectionIdInvalid();
      throw new InvalidBindingException("Invalid server name: " + serverId);
    }

    ConnectedSonarLintEngine engine = getConnectedEngine(serverId);
    checkConnectedEngineStatus(engine, notifications, serverId, projectKey);
    return engine;
  }

  private static Set<String> getServerNames() {
    return getGlobalSettings().getServerConnections().stream()
      .map(ServerConnection::getName)
      .collect(Collectors.toSet());
  }

  @Override
  public void dispose() {
    stopAllEngines(false);
    Loggers.setTarget(null);
  }

  @Nullable
  public ConnectedSonarLintEngine getConnectedEngineIfStarted(String connectionId) {
    return connectedEngines.get(connectionId);
  }

  @Nullable
  public SonarLintEngine getStandaloneEngineIfStarted() {
    return standalone;
  }
}
