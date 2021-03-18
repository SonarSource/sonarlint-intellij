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

import com.intellij.openapi.application.PathManager;
import java.io.IOException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sonarlint.intellij.SonarLintPlugin;
import org.sonarlint.intellij.common.LanguageActivator;
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

  private static final Set<Language> STANDALONE_LANGUAGES = EnumSet.of(Language.HTML,
    Language.JAVA,
    Language.JS,
    Language.KOTLIN,
    Language.PHP,
    Language.PYTHON,
    Language.RUBY,
    Language.TS);

  private static final Set<Language> CONNECTED_ADDITIONAL_LANGUAGES = EnumSet.of(
    Language.SCALA,
    Language.SWIFT,
    Language.XML
  );

  ConnectedSonarLintEngine createEngine(String serverId) {
    Set<Language> enabledLanguages = EnumSet.copyOf(STANDALONE_LANGUAGES);
    enabledLanguages.addAll(CONNECTED_ADDITIONAL_LANGUAGES);

    amendEnabledLanguages(enabledLanguages);

    GlobalLogOutput globalLogOutput = SonarLintUtils.getService(GlobalLogOutput.class);
    final NodeJsManager nodeJsManager = SonarLintUtils.getService(NodeJsManager.class);
    ConnectedGlobalConfiguration config = ConnectedGlobalConfiguration.builder()
      .setLogOutput(globalLogOutput)
      .setSonarLintUserHome(getSonarLintHome())
      .addEnabledLanguages(enabledLanguages.toArray(new Language[0]))
      .setExtraProperties(prepareExtraProps())
      .setNodeJs(nodeJsManager.getNodeJsPath(), nodeJsManager.getNodeJsVersion())
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

      Set<Language> enabledLanguages = EnumSet.copyOf(STANDALONE_LANGUAGES);

      amendEnabledLanguages(enabledLanguages);

      GlobalLogOutput globalLogOutput = SonarLintUtils.getService(GlobalLogOutput.class);
      final NodeJsManager nodeJsManager = SonarLintUtils.getService(NodeJsManager.class);
      StandaloneGlobalConfiguration globalConfiguration = StandaloneGlobalConfiguration.builder()
        .setLogOutput(globalLogOutput)
        .setSonarLintUserHome(getSonarLintHome())
        .setWorkDir(getWorkDir())
        .addPlugins(plugins)
        .addEnabledLanguages(enabledLanguages.toArray(new Language[0]))
        .setExtraProperties(prepareExtraProps())
        .setNodeJs(nodeJsManager.getNodeJsPath(), nodeJsManager.getNodeJsVersion())
        .build();

      return new StandaloneSonarLintEngineImpl(globalConfiguration);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    } finally {
      Thread.currentThread().setContextClassLoader(cl);
    }
  }

  private static void amendEnabledLanguages(Set<Language> enabledLanguages) {
    List<LanguageActivator> languageActivator = LanguageActivator.EP_NAME.getExtensionList();
    languageActivator.forEach(l -> l.amendLanguages(enabledLanguages));
  }

  private static URL[] loadPlugins() throws IOException {
    SonarLintPlugin plugin = SonarLintUtils.getService(SonarLintPlugin.class);
    return getPluginsUrls(plugin.getPath().resolve("plugins"));
  }

  private static URL[] getPluginsUrls(Path pluginsDir) throws IOException {
    List<URL> pluginsUrls = new ArrayList<>();

    if (Files.isDirectory(pluginsDir)) {
      try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(pluginsDir, "*.jar")) {
        GlobalLogOutput globalLogOutput = SonarLintUtils.getService(GlobalLogOutput.class);
        for (Path path : directoryStream) {
          globalLogOutput.log("Found plugin: " + path.getFileName().toString(), LogOutput.Level.DEBUG);
          pluginsUrls.add(path.toUri().toURL());
        }
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

  private static Map<String, String> prepareExtraProps() {
    SonarLintPlugin plugin = SonarLintUtils.getService(SonarLintPlugin.class);
    return Collections.singletonMap("sonar.typescript.internal.typescriptLocation", plugin.getPath().toString());
  }
}
