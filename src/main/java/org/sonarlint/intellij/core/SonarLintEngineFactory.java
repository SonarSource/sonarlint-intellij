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

import com.intellij.openapi.application.PathManager;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.apache.commons.lang.StringUtils;
import org.sonarlint.intellij.SonarLintPlugin;
import org.sonarlint.intellij.config.Settings;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

public class SonarLintEngineFactory  {

  private static final Language[] STANDALONE_LANGUAGES = {
    Language.HTML,
    Language.JAVA,
    Language.JS,
    Language.KOTLIN,
    Language.PHP,
    Language.PYTHON,
    Language.RUBY,
    Language.TS
  };

  private static final Language[] CONNECTED_ADDITIONAL_LANGUAGES = {
    Language.SCALA,
    Language.SWIFT,
    Language.XML
  };


  ConnectedSonarLintEngine createEngine(String serverId) {
    GlobalLogOutput globalLogOutput = SonarLintUtils.getService(GlobalLogOutput.class);
    ConnectedGlobalConfiguration config = ConnectedGlobalConfiguration.builder()
      .setLogOutput(globalLogOutput)
      .setSonarLintUserHome(getSonarLintHome())
      .addEnabledLanguages(STANDALONE_LANGUAGES)
      .addEnabledLanguages(CONNECTED_ADDITIONAL_LANGUAGES)
      .setExtraProperties(prepareExtraProps())
      .setNodeJsPath(getNodeJsPathFromConfig())
      .setWorkDir(getWorkDir())
      .setServerId(serverId)
      .build();

    // it will also start it
    return new ConnectedSonarLintEngineImpl(config);
  }

  StandaloneSonarLintEngine createEngine() {
    /*
     * Some components in the container use the context classloader to find resources. For example, the ServiceLoader uses it by default
     * to find services declared by some libs.
     */
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

    try {
      URL[] plugins = loadPlugins();

      GlobalLogOutput globalLogOutput = SonarLintUtils.getService(GlobalLogOutput.class);
      StandaloneGlobalConfiguration globalConfiguration = StandaloneGlobalConfiguration.builder()
        .setLogOutput(globalLogOutput)
        .setSonarLintUserHome(getSonarLintHome())
        .setWorkDir(getWorkDir())
        .addPlugins(plugins)
        .addEnabledLanguages(STANDALONE_LANGUAGES)
        .setExtraProperties(prepareExtraProps())
        .setNodeJsPath(getNodeJsPathFromConfig())
        .build();

      return new StandaloneSonarLintEngineImpl(globalConfiguration);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    } finally {
      Thread.currentThread().setContextClassLoader(cl);
    }
  }

  @CheckForNull
  private Path getNodeJsPathFromConfig() {
    final String nodejsPathStr = Settings.getGlobalSettings().getNodejsPath();
    if (StringUtils.isNotBlank(nodejsPathStr)) {
      try {
        return Paths.get(nodejsPathStr);
      } catch (Exception e) {
        throw new IllegalStateException("Invalid Node.js path", e);
      }
    }
    return null;
  }

  private URL[] loadPlugins() throws IOException, URISyntaxException {
    URL pluginsDir = this.getClass().getClassLoader().getResource("plugins");
    if (pluginsDir == null) {
      throw new IllegalStateException("Couldn't find plugins");
    }

    if ("file".equalsIgnoreCase(pluginsDir.toURI().getScheme())) {
      return getPluginsUrls(pluginsDir);
    } else {
      return getPluginsUrlsWithFs(pluginsDir);
    }
  }

  private URL[] getPluginsUrlsWithFs(URL pluginsDir) throws IOException, URISyntaxException {
    Map<String, String> env = new HashMap<>();
    env.put("create", "true");
    try (FileSystem fs = FileSystems.newFileSystem(pluginsDir.toURI(), env)) {
      return getPluginsUrls(pluginsDir);
    }
  }

  private URL[] getPluginsUrls(URL pluginsDir) throws IOException, URISyntaxException {
    List<URL> pluginsUrls = new ArrayList<>();

    try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(Paths.get(pluginsDir.toURI()), "*.jar")) {
      GlobalLogOutput globalLogOutput = SonarLintUtils.getService(GlobalLogOutput.class);
      for (Path path : directoryStream) {
        globalLogOutput.log("Found plugin: " + path.getFileName().toString(), LogOutput.Level.DEBUG);

        URL newUrl;
        if ("file".equalsIgnoreCase(pluginsDir.toURI().getScheme())) {
          newUrl = path.toUri().toURL();
        } else {
          // any attempt to convert path directly to URL or URI will result in having spaces double escaped
          newUrl = new URL(pluginsDir, path.toString());
        }
        pluginsUrls.add(newUrl);
      }
    }
    return pluginsUrls.toArray(new URL[0]);
  }

  private static Path getSonarLintHome() {
    return Paths.get(PathManager.getConfigPath()).resolve("sonarlint");
  }

  private static Path getWorkDir() {
    return Paths.get(PathManager.getTempPath()).resolve("sonarlint");
  }

  private Map<String, String> prepareExtraProps() {
    SonarLintPlugin plugin = SonarLintUtils.getService(SonarLintPlugin.class);
    return Collections.singletonMap("sonar.typescript.internal.typescriptLocation", plugin.getPath().toString());
  }
}
