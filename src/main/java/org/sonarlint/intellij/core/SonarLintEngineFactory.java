/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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

import com.intellij.execution.process.OSProcessUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.util.PlatformUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.SonarLintPlugin;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.module.ModulesRegistry;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.AbstractGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.core.commons.Language;

public class SonarLintEngineFactory {

  ConnectedSonarLintEngine createEngine(String connectionId, boolean isSonarCloud) {
    var modulesRegistry = SonarLintUtils.getService(ModulesRegistry.class);

    var configBuilder = isSonarCloud ? ConnectedGlobalConfiguration.sonarCloudBuilder() : ConnectedGlobalConfiguration.sonarQubeBuilder();
    configBuilder
      .addEnabledLanguages(EmbeddedPlugins.getEnabledLanguagesInConnectedMode().toArray(new Language[0]))
      .enableHotspots()
      .setConnectionId(connectionId)
      .setModulesProvider(() -> modulesRegistry.getModulesForEngine(connectionId));
    configureCommonEngine(configBuilder);

    EmbeddedPlugins.getEmbeddedPluginsForConnectedMode().forEach(configBuilder::useEmbeddedPlugin);
    EmbeddedPlugins.getExtraPluginsForConnectedMode().forEach(configBuilder::addExtraPlugin);

    return new ConnectedSonarLintEngineImpl(configBuilder.build());
  }

  StandaloneSonarLintEngine createEngine() {
    /*
     * Some components in the container use the context classloader to find resources. For example, the ServiceLoader uses it by default
     * to find services declared by some libs.
     */
    var cl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

    try {
      var plugins = EmbeddedPlugins.findEmbeddedPlugins();

      var modulesRegistry = SonarLintUtils.getService(ModulesRegistry.class);

      var configBuilder = StandaloneGlobalConfiguration.builder()
        .addPlugins(plugins.toArray(new Path[0]))
        .addEnabledLanguages(EmbeddedPlugins.getEnabledLanguagesInStandaloneMode().toArray(new Language[0]))
        .setModulesProvider(modulesRegistry::getStandaloneModules);
      configureCommonEngine(configBuilder);

      return new StandaloneSonarLintEngineImpl(configBuilder.build());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    } finally {
      Thread.currentThread().setContextClassLoader(cl);
    }
  }

  private static void configureCommonEngine(AbstractGlobalConfiguration.AbstractBuilder<?> builder) {
    var globalLogOutput = SonarLintUtils.getService(GlobalLogOutput.class);
    final var nodeJsManager = SonarLintUtils.getService(NodeJsManager.class);
    builder
      .setLogOutput(globalLogOutput)
      .setSonarLintUserHome(getSonarLintHome())
      .setWorkDir(getWorkDir())
      .setExtraProperties(prepareExtraProps())
      .setNodeJs(nodeJsManager.getNodeJsPath(), nodeJsManager.getNodeJsVersion())
      .setClientPid(OSProcessUtil.getCurrentProcessId());
  }

  private static Path getSonarLintHome() {
    migrate();
    return getSonarlintSystemPath();
  }

  /**
   * SLI-657
   */
  private static synchronized void migrate() {
    var oldPath = Paths.get(PathManager.getConfigPath()).resolve("sonarlint");
    var newPath = getSonarlintSystemPath();
    if (Files.exists(oldPath) && !Files.exists(newPath)) {
      try {
        FileUtils.moveDirectory(oldPath.toFile(), newPath.toFile());
      } catch (IOException e) {
        SonarLintUtils.getService(GlobalLogOutput.class).logError("Unable to migrate storage", e);
      } finally {
        FileUtils.deleteQuietly(oldPath.toFile());
      }
    }
  }

  @NotNull
  private static Path getSonarlintSystemPath() {
    return Paths.get(PathManager.getSystemPath()).resolve("sonarlint");
  }

  private static Path getWorkDir() {
    return Paths.get(PathManager.getTempPath()).resolve("sonarlint");
  }

  private static Map<String, String> prepareExtraProps() {
    var plugin = SonarLintUtils.getService(SonarLintPlugin.class);
    var extraProps = new HashMap<String, String>();
    if (PlatformUtils.isRider()) {
      addOmnisharpServerPaths(plugin, extraProps);
    }
    return extraProps;
  }

  private static void addOmnisharpServerPaths(SonarLintPlugin plugin, Map<String, String> extraProps) {
    extraProps.put("sonar.cs.internal.omnisharpMonoLocation", plugin.getPath().resolve("omnisharp/mono").toString());
    extraProps.put("sonar.cs.internal.omnisharpWinLocation", plugin.getPath().resolve("omnisharp/win").toString());
    extraProps.put("sonar.cs.internal.omnisharpNet6Location", plugin.getPath().resolve("omnisharp/net6").toString());
  }

}
