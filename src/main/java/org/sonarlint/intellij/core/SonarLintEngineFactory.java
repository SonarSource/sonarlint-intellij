/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.SonarLintPlugin;
import org.sonarlint.intellij.common.LanguageActivator;
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
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput;

public class SonarLintEngineFactory {

  private static final Set<Language> STANDALONE_LANGUAGES = EnumSet.of(
    Language.HTML,
    Language.XML,
    Language.JS,
    Language.KOTLIN,
    Language.PHP,
    Language.PYTHON,
    Language.RUBY,
    Language.SECRETS,
    Language.TS);

  private static final Set<Language> CONNECTED_ADDITIONAL_LANGUAGES = EnumSet.of(
    Language.SCALA,
    Language.SWIFT);

  private static final List<EmbeddedPlugin> EMBEDDED_PLUGINS = List.of(
    new EmbeddedPlugin(Language.CPP, "CFamily", "sonar-cfamily-plugin-*.jar"),
    new EmbeddedPlugin(Language.SECRETS, "Secrets detection", "sonar-secrets-plugin-*.jar"),
    new EmbeddedPlugin(Language.CS, "CSharp", "sonarlint-omnisharp-plugin-*.jar"),
    new EmbeddedPlugin(Language.HTML, "HTML", "sonar-html-plugin-*.jar"),
    new EmbeddedPlugin(Language.JS, "JavaScript/TypeScript", "sonar-javascript-plugin-*.jar"),
    new EmbeddedPlugin(Language.KOTLIN, "Kotlin", "sonar-kotlin-plugin-*.jar"),
    new EmbeddedPlugin(Language.RUBY, "Ruby", "sonar-ruby-plugin-*.jar"),
    new EmbeddedPlugin(Language.XML, "XML", "sonar-xml-plugin-*.jar"));

  ConnectedSonarLintEngine createEngine(String connectionId, boolean isSonarCloud) {
    var enabledLanguages = EnumSet.copyOf(STANDALONE_LANGUAGES);
    enabledLanguages.addAll(CONNECTED_ADDITIONAL_LANGUAGES);

    amendEnabledLanguages(enabledLanguages);

    var modulesRegistry = SonarLintUtils.getService(ModulesRegistry.class);

    var configBuilder = isSonarCloud ? ConnectedGlobalConfiguration.sonarCloudBuilder() : ConnectedGlobalConfiguration.sonarQubeBuilder();
    configBuilder
      .addEnabledLanguages(enabledLanguages.toArray(new Language[0]))
      .setConnectionId(connectionId)
      .setModulesProvider(() -> modulesRegistry.getModulesForEngine(connectionId));
    configureCommonEngine(configBuilder);

    var pluginsDir = getPluginsDir();
    EMBEDDED_PLUGINS.forEach(embeddedPlugin -> {
      var embeddedPluginUrl = findEmbeddedPlugin(pluginsDir, embeddedPlugin);
      if (embeddedPluginUrl != null) {
        configBuilder.useEmbeddedPlugin(embeddedPlugin.pluginKey, embeddedPluginUrl);
      }
    });
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
      var plugins = findEmbeddedPlugins();

      var enabledLanguages = EnumSet.copyOf(STANDALONE_LANGUAGES);

      amendEnabledLanguages(enabledLanguages);

      var modulesRegistry = SonarLintUtils.getService(ModulesRegistry.class);

      var configBuilder = StandaloneGlobalConfiguration.builder()
        .addPlugins(plugins)
        .addEnabledLanguages(enabledLanguages.toArray(new Language[0]))
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

  private static void amendEnabledLanguages(Set<Language> enabledLanguages) {
    var languageActivator = LanguageActivator.EP_NAME.getExtensionList();
    languageActivator.forEach(l -> l.amendLanguages(enabledLanguages));
  }

  private static Path[] findEmbeddedPlugins() throws IOException {
    return getPluginsUrls(getPluginsDir());
  }

  @NotNull
  private static Path getPluginsDir() {
    var plugin = SonarLintUtils.getService(SonarLintPlugin.class);
    return plugin.getPath().resolve("plugins");
  }

  @CheckForNull
  private static Path findEmbeddedPlugin(Path pluginsDir, EmbeddedPlugin embeddedPlugin) {
    try {
      var pluginsUrls = findFilesInDir(pluginsDir, embeddedPlugin.jarFilePattern, "Found " + embeddedPlugin.name + " plugin: ");
      if (pluginsUrls.size() > 1) {
        throw new IllegalStateException("Multiple plugins found");
      }
      return pluginsUrls.size() == 1 ? pluginsUrls.get(0) : null;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static Path[] getPluginsUrls(Path pluginsDir) throws IOException {
    return findFilesInDir(pluginsDir, "*.jar", "Found plugin: ").toArray(new Path[0]);
  }

  private static List<Path> findFilesInDir(Path pluginsDir, String pattern, String logPrefix) throws IOException {
    var pluginsPaths = new ArrayList<Path>();
    if (Files.isDirectory(pluginsDir)) {
      try (var directoryStream = Files.newDirectoryStream(pluginsDir, pattern)) {
        var globalLogOutput = SonarLintUtils.getService(GlobalLogOutput.class);
        for (var path : directoryStream) {
          globalLogOutput.log(logPrefix + path.getFileName().toString(), ClientLogOutput.Level.DEBUG);
          pluginsPaths.add(path);
        }
      }
    }
    return pluginsPaths;
  }

  private static Path getSonarLintHome() {
    return Paths.get(PathManager.getConfigPath()).resolve("sonarlint");
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

  private static class EmbeddedPlugin {
    private final String pluginKey;
    private final String name;
    private final String jarFilePattern;

    private EmbeddedPlugin(Language language, String name, String jarFilePattern) {
      this.pluginKey = language.getPluginKey();
      this.name = name;
      this.jarFilePattern = jarFilePattern;
    }
  }
}
