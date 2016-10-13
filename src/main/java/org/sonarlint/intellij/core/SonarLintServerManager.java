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
package org.sonarlint.intellij.core;

import com.google.common.base.Preconditions;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.annotation.ThreadSafe;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.config.global.SonarQubeServer;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ModuleUpdateStatus;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

@ThreadSafe
public class SonarLintServerManager implements ApplicationComponent {
  private Map<String, ConnectedSonarLintEngine> engines;
  private StandaloneSonarLintEngine standalone;
  private GlobalLogOutput globalLogOutput;
  private SonarLintGlobalSettings settings;
  private Set<String> configuredStorageIds;

  public SonarLintServerManager(GlobalLogOutput globalLogOutput, SonarLintGlobalSettings settings) {
    this.globalLogOutput = globalLogOutput;
    this.settings = settings;
  }

  @Override
  public void initComponent() {
    configuredStorageIds = new HashSet<>();
    reloadServerNames();
    engines = new HashMap<>();
  }

  /**
   * Immediately removes and asynchronously stops all {@link ConnectedSonarLintEngine} corresponding to server IDs that were removed.
   */
  public synchronized void reloadServers() {
    reloadServerNames();
    Iterator<Map.Entry<String, ConnectedSonarLintEngine>> it = engines.entrySet().iterator();

    while (it.hasNext()) {
      Map.Entry<String, ConnectedSonarLintEngine> e = it.next();
      if (!configuredStorageIds.contains(e.getKey())) {
        stopInThread(e.getValue());
        it.remove();
      }
    }
  }

  public synchronized ConnectedSonarLintEngine getConnectedEngine(String serverId) {
    if (!engines.containsKey(serverId)) {
      ConnectedSonarLintEngine engine = createEngine(serverId);

      engines.put(serverId, engine);
    }

    return engines.get(serverId);
  }

  public synchronized StandaloneSonarLintEngine getStandaloneEngine() {
    if (standalone == null) {
      standalone = createEngine();
    }
    return standalone;
  }

  /**
   * Will create a Facade with the appropriate engine (standalone or connected) based on the current project and module configurations.
   * In case of a problem, it handles the displaying of errors (Logging, user notifications, ..) and throws an IllegalStateException.
   */
  public synchronized SonarLintFacade getFacadeForAnalysis(Project project) {
    SonarLintProjectSettings projectSettings = SonarLintUtils.get(project, SonarLintProjectSettings.class);
    SonarLintConsole console = SonarLintUtils.get(project, SonarLintConsole.class);
    if (projectSettings.isBindingEnabled()) {
      String serverId = projectSettings.getServerId();
      String projectKey = projectSettings.getProjectKey();

      if (serverId == null) {
        SonarLintProjectNotifications.get(project).notifyServerIdInvalid();
        throw new IllegalStateException("Project has an invalid binding");
      } else if (projectKey == null) {
        SonarLintProjectNotifications.get(project).notifyModuleInvalid();
        throw new IllegalStateException("Project has an invalid binding");
      } else {
        console.info(String.format("Using configuration of '%s' in server '%s'", projectSettings.getProjectKey(), projectSettings.getServerId()));
        return createConnectedFacade(project, serverId, projectKey);
      }
    }
    return new StandaloneSonarLintFacade(project, getStandaloneEngine());
  }

  private static void stopInThread(final ConnectedSonarLintEngine engine) {
    new Thread("stop-sonarlint-engine") {
      @Override
      public void run() {
        engine.stop(false);
      }
    }.start();
  }

  private SonarLintFacade createConnectedFacade(Project project, String serverId, String projectKey) {
    Preconditions.checkNotNull(project, "project");
    Preconditions.checkNotNull(serverId, "serverId");
    Preconditions.checkNotNull(projectKey, "projectKey");

    if (!configuredStorageIds.contains(serverId)) {
      SonarLintProjectNotifications.get(project).notifyServerIdInvalid();
      throw new IllegalStateException("Invalid server name: " + serverId);
    }

    ConnectedSonarLintEngine engine;
    if (engines.containsKey(serverId)) {
      engine = engines.get(serverId);
    } else {
      engine = createEngine(serverId);
      engines.put(serverId, engine);
    }

    // Check if engine's global storage is OK
    ConnectedSonarLintEngine.State state = engine.getState();
    if (state != ConnectedSonarLintEngine.State.UPDATED) {
      if (state != ConnectedSonarLintEngine.State.NEED_UPDATE) {
        SonarLintProjectNotifications.get(project).notifyServerNotUpdated();
      } else if (state != ConnectedSonarLintEngine.State.NEVER_UPDATED) {
        SonarLintProjectNotifications.get(project).notifyServerNeedsUpdate(serverId);
      }
      throw new IllegalStateException("Server is not updated: " + serverId);
    }

    // Check if module's storage is OK. Global storage was updated and all project's binding that were open too,
    // but we might have now opened a new project with a different binding.
    ModuleUpdateStatus moduleUpdateStatus = engine.getModuleUpdateStatus(projectKey);

    if (moduleUpdateStatus == null) {
      SonarLintProjectNotifications.get(project).notifyModuleInvalid();
      throw new IllegalStateException("Project is bound to a module that doesn't exist: " + projectKey);
    } else if (moduleUpdateStatus.isStale()) {
      SonarLintProjectNotifications.get(project).notifyModuleStale();
      throw new IllegalStateException("Stale module's storage: " + projectKey);
    }

    return new ConnectedSonarLintFacade(engine, project, projectKey);
  }

  private static Path getSonarLintHome() {
    return Paths.get(PathManager.getConfigPath()).resolve("sonarlint");
  }

  private static Path getWorkDir() {
    return Paths.get(PathManager.getTempPath()).resolve("sonarlint");
  }

  private StandaloneSonarLintEngineImpl createEngine() {
    /*
     * Some components in the container use the context classloader to find resources. For example, the ServiceLoader uses it by default
     * to find services declared by some libs.
     */
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

    try {
      URL[] plugins = loadPlugins();

      StandaloneGlobalConfiguration globalConfiguration = StandaloneGlobalConfiguration.builder()
        .setLogOutput(globalLogOutput)
        .setSonarLintUserHome(getSonarLintHome())
        .setWorkDir(getWorkDir())
        .addPlugins(plugins)
        .build();

      return new StandaloneSonarLintEngineImpl(globalConfiguration);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    } finally {
      Thread.currentThread().setContextClassLoader(cl);
    }
  }

  private ConnectedSonarLintEngine createEngine(String serverId) {
    ConnectedGlobalConfiguration config = ConnectedGlobalConfiguration.builder()
      .setLogOutput(globalLogOutput)
      .setSonarLintUserHome(getSonarLintHome())
      .setWorkDir(getWorkDir())
      .setServerId(serverId)
      .build();

    // it will also start it
    return new ConnectedSonarLintEngineImpl(config);
  }

  private URL[] loadPlugins() throws IOException, URISyntaxException {
    URL pluginsDir = this.getClass().getClassLoader().getResource("plugins");

    if (pluginsDir == null) {
      throw new IllegalStateException("Couldn't find plugins");
    }

    List<URL> pluginsUrls = new ArrayList<>();
    try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Paths.get(pluginsDir.toURI()), "*.jar")) {
      for (Path path : directoryStream) {
        globalLogOutput.log("Found plugin: " + path.getFileName().toString(), LogOutput.Level.DEBUG);
        pluginsUrls.add(path.toUri().toURL());
      }
    }
    return pluginsUrls.toArray(new URL[pluginsUrls.size()]);
  }

  private void reloadServerNames() {
    configuredStorageIds.clear();
    for (SonarQubeServer s : settings.getSonarQubeServers()) {
      configuredStorageIds.add(s.getName());
    }
  }

  @Override
  public void disposeComponent() {
    for (ConnectedSonarLintEngine e : engines.values()) {
      e.stop(false);
    }
    engines.clear();
    if (standalone != null) {
      standalone.stop();
      standalone = null;
    }
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "SonarLintServerManager";
  }
}
