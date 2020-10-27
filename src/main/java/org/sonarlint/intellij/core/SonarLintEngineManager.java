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
package org.sonarlint.intellij.core;

import com.google.common.base.Preconditions;
import com.intellij.openapi.Disposable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.sonar.api.utils.log.Loggers;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.exception.InvalidBindingException;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ProjectStorageStatus;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

import static org.sonarlint.intellij.config.Settings.getGlobalSettings;

public class SonarLintEngineManager implements Disposable {
  private final Map<String, ConnectedSonarLintEngine> engines = new HashMap<>();
  private final SonarLintEngineFactory factory;
  private StandaloneSonarLintEngine standalone;

  public SonarLintEngineManager() {
    this(new SonarLintEngineFactory());
  }

  /**
   * Replace by @NonInjectable when supported
   * @deprecated
   */
  @Deprecated
  SonarLintEngineManager(SonarLintEngineFactory factory) {
    this.factory = factory;
  }

  private static void stopInThread(final ConnectedSonarLintEngine engine) {
    new Thread("stop-sonarlint-engine") {
      @Override
      public void run() {
        engine.stop(false);
      }
    }.start();
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
  public synchronized void stopAllDeletedConnectedEngines() {
    Iterator<Map.Entry<String, ConnectedSonarLintEngine>> it = engines.entrySet().iterator();
    Set<String> configuredStorageIds = getServerNames();
    while (it.hasNext()) {
      Map.Entry<String, ConnectedSonarLintEngine> e = it.next();
      if (!configuredStorageIds.contains(e.getKey())) {
        stopInThread(e.getValue());
        it.remove();
      }
    }
  }

  public synchronized void stopAllEngines() {
    AnalysisRequirementNotifications.resetCachedMessages();
    for (ConnectedSonarLintEngine e : engines.values()) {
      e.stop(false);
    }
    engines.clear();
    if (standalone != null) {
      standalone.stop();
      standalone = null;
    }
  }

  public synchronized ConnectedSonarLintEngine getConnectedEngine(String serverId) {
    if (!engines.containsKey(serverId)) {
      ConnectedSonarLintEngine engine = factory.createEngine(serverId);
      engines.put(serverId, engine);
    }

    return engines.get(serverId);
  }

  public synchronized StandaloneSonarLintEngine getStandaloneEngine() {
    if (standalone == null) {
      standalone = factory.createEngine();
    }
    return standalone;
  }

  public synchronized ConnectedSonarLintEngine getConnectedEngine(SonarLintProjectNotifications notifications, String serverId, String projectKey) throws InvalidBindingException {
    Preconditions.checkNotNull(notifications, "notifications");
    Preconditions.checkNotNull(serverId, "serverId");
    Preconditions.checkNotNull(projectKey, "projectKey");

    Set<String> configuredStorageIds = getServerNames();
    if (!configuredStorageIds.contains(serverId)) {
      notifications.notifyServerIdInvalid();
      throw new InvalidBindingException("Invalid server name: " + serverId);
    }

    ConnectedSonarLintEngine engine = getConnectedEngine(serverId);
    checkConnectedEngineStatus(engine, notifications, serverId, projectKey);
    return engine;
  }

  private static Set<String> getServerNames() {
    return getGlobalSettings().getSonarQubeServers().stream()
      .map(SonarQubeServer::getName)
      .collect(Collectors.toSet());
  }

  @Override
  public void dispose() {
    stopAllEngines();
    Loggers.setTarget(null);
  }
}
