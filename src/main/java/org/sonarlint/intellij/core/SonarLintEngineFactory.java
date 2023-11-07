/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sonarlint.intellij.SonarLintPlugin;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.module.ModulesRegistry;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarsource.sonarlint.core.analysis.api.ClientModulesProvider;
import org.sonarsource.sonarlint.core.client.legacy.analysis.EngineConfiguration;
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class SonarLintEngineFactory {
  SonarLintAnalysisEngine createEngineForConnection(String connectionId) {
    var modulesRegistry = getService(ModulesRegistry.class);
    return configureEngine(() -> modulesRegistry.getModulesForEngine(connectionId), connectionId);
  }

  SonarLintAnalysisEngine createStandaloneEngine() {
    /*
     * Some components in the container use the context classloader to find resources. For example, the ServiceLoader uses it by default
     * to find services declared by some libs.
     */
    var cl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
    var modulesRegistry = getService(ModulesRegistry.class);
    try {
      return configureEngine(modulesRegistry::getStandaloneModules, null);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    } finally {
      Thread.currentThread().setContextClassLoader(cl);
    }
  }

  private static SonarLintAnalysisEngine configureEngine(ClientModulesProvider modulesProvider, @Nullable String connectionId) {
    var engineConfiguration = EngineConfiguration.builder()
      .setLogOutput(getService(GlobalLogOutput.class))
      .setSonarLintUserHome(getSonarLintHome())
      .setWorkDir(getWorkDir())
      .setExtraProperties(prepareExtraProps())
      .setClientPid(OSProcessUtil.getCurrentProcessId())
      .setModulesProvider(modulesProvider)
      .build();
    return getService(BackendService.class).createEngine(engineConfiguration, connectionId);
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
        getService(GlobalLogOutput.class).logError("Unable to migrate storage", e);
      } finally {
        FileUtils.deleteQuietly(oldPath.toFile());
      }
    }
  }

  @NotNull
  private static Path getSonarlintSystemPath() {
    return Paths.get(PathManager.getSystemPath()).resolve("sonarlint");
  }

  public static Path getWorkDir() {
    return Paths.get(PathManager.getTempPath()).resolve("sonarlint");
  }

  private static Map<String, String> prepareExtraProps() {
    var plugin = getService(SonarLintPlugin.class);
    var extraProps = new HashMap<String, String>();
    if (SonarLintUtils.isRider()) {
      addOmnisharpServerPaths(plugin, extraProps);
    }
    return extraProps;
  }

  private static void addOmnisharpServerPaths(SonarLintPlugin plugin, Map<String, String> extraProps) {
    extraProps.put("sonar.cs.internal.omnisharpMonoLocation", plugin.getPath().resolve("omnisharp/mono").toString());
    extraProps.put("sonar.cs.internal.omnisharpWinLocation", plugin.getPath().resolve("omnisharp/net472").toString());
    extraProps.put("sonar.cs.internal.omnisharpNet6Location", plugin.getPath().resolve("omnisharp/net6").toString());
  }

}
