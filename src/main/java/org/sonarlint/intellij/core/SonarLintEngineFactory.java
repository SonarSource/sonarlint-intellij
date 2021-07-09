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

import com.intellij.execution.process.OSProcessUtil;
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
import javax.annotation.CheckForNull;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.SonarLintPlugin;
import org.sonarlint.intellij.common.LanguageActivator;
import org.sonarlint.intellij.module.ModulesRegistry;
import org.sonarlint.intellij.util.GlobalLogOutput;
import org.sonarlint.intellij.util.SonarLintUtils;
import org.sonarsource.sonarlint.core.ConnectedSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.AbstractGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;

public class SonarLintEngineFactory {

  private static final Set<Language> STANDALONE_LANGUAGES = EnumSet.of(Language.HTML,
    Language.JS,
    Language.KOTLIN,
    Language.PHP,
    Language.PYTHON,
    Language.RUBY,
    Language.SECRETS,
    Language.TS);

  private static final Set<Language> CONNECTED_ADDITIONAL_LANGUAGES = EnumSet.of(
    Language.SCALA,
    Language.SWIFT,
    Language.XML);

  ConnectedSonarLintEngine createEngine(String connectionId) {
    Set<Language> enabledLanguages = EnumSet.copyOf(STANDALONE_LANGUAGES);
    enabledLanguages.addAll(CONNECTED_ADDITIONAL_LANGUAGES);

    amendEnabledLanguages(enabledLanguages);

    ModulesRegistry modulesRegistry = SonarLintUtils.getService(ModulesRegistry.class);

    URL cFamilyPluginUrl = findEmbeddedCFamilyPlugin(getPluginsDir());
    ConnectedGlobalConfiguration.Builder configBuilder = ConnectedGlobalConfiguration.builder()
      .addEnabledLanguages(enabledLanguages.toArray(new Language[0]))
      .setConnectionId(connectionId)
      .setModulesProvider(() -> modulesRegistry.getModulesForEngine(connectionId));
    configureCommonEngine(configBuilder);

    if (cFamilyPluginUrl != null) {
      configBuilder.useEmbeddedPlugin(Language.CPP.getPluginKey(), cFamilyPluginUrl);
    }
    URL secretsPluginUrl = findEmbeddedSecretsPlugin(getPluginsDir());
    if (secretsPluginUrl != null) {
      configBuilder.addExtraPlugin(Language.SECRETS.getPluginKey(), secretsPluginUrl);
    }
    // it will also start it
    return new ConnectedSonarLintEngineImpl(configBuilder.build());
  }

  StandaloneSonarLintEngine createEngine() {
    /*
     * Some components in the container use the context classloader to find resources. For example, the ServiceLoader uses it by default
     * to find services declared by some libs.
     */
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

    try {
      URL[] plugins = findEmbeddedPlugins();

      Set<Language> enabledLanguages = EnumSet.copyOf(STANDALONE_LANGUAGES);

      amendEnabledLanguages(enabledLanguages);

      ModulesRegistry modulesRegistry = SonarLintUtils.getService(ModulesRegistry.class);

      StandaloneGlobalConfiguration.Builder configBuilder = StandaloneGlobalConfiguration.builder()
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
    GlobalLogOutput globalLogOutput = SonarLintUtils.getService(GlobalLogOutput.class);
    final NodeJsManager nodeJsManager = SonarLintUtils.getService(NodeJsManager.class);
    builder
      .setLogOutput(globalLogOutput)
      .setSonarLintUserHome(getSonarLintHome())
      .setWorkDir(getWorkDir())
      .setExtraProperties(prepareExtraProps())
      .setNodeJs(nodeJsManager.getNodeJsPath(), nodeJsManager.getNodeJsVersion())
      .setClientPid(OSProcessUtil.getCurrentProcessId());
  }

  private static void amendEnabledLanguages(Set<Language> enabledLanguages) {
    List<LanguageActivator> languageActivator = LanguageActivator.EP_NAME.getExtensionList();
    languageActivator.forEach(l -> l.amendLanguages(enabledLanguages));
  }

  private static URL[] findEmbeddedPlugins() throws IOException {
    return getPluginsUrls(getPluginsDir());
  }

  @NotNull
  private static Path getPluginsDir() {
    SonarLintPlugin plugin = SonarLintUtils.getService(SonarLintPlugin.class);
    return plugin.getPath().resolve("plugins");
  }

  @CheckForNull
  private static URL findEmbeddedPlugin(Path pluginsDir, String pluginNamePattern, String logPrefix) {
    try {
      List<URL> pluginsUrls = findFilesInDir(pluginsDir, pluginNamePattern, logPrefix);
      if (pluginsUrls.size() > 1) {
        throw new IllegalStateException("Multiple plugins found");
      }
      return pluginsUrls.size() == 1 ? pluginsUrls.get(0) : null;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @CheckForNull
  private static URL findEmbeddedCFamilyPlugin(Path pluginsDir) {
    return findEmbeddedPlugin(pluginsDir, "sonar-cfamily-plugin-*.jar", "Found CFamily plugin: ");
  }

  @CheckForNull
  private static URL findEmbeddedSecretsPlugin(Path pluginsDir) {
    return findEmbeddedPlugin(pluginsDir, "sonar-secrets-plugin-*.jar", "Found Secrets detection plugin: ");
  }

  private static URL[] getPluginsUrls(Path pluginsDir) throws IOException {
    return findFilesInDir(pluginsDir, "*.jar", "Found plugin: ").toArray(new URL[0]);
  }

  private static List<URL> findFilesInDir(Path pluginsDir, String pattern, String logPrefix) throws IOException {
    List<URL> pluginsUrls = new ArrayList<>();
    if (Files.isDirectory(pluginsDir)) {
      try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(pluginsDir, pattern)) {
        GlobalLogOutput globalLogOutput = SonarLintUtils.getService(GlobalLogOutput.class);
        for (Path path : directoryStream) {
          globalLogOutput.log(logPrefix + path.getFileName().toString(), LogOutput.Level.DEBUG);
          pluginsUrls.add(path.toUri().toURL());
        }
      }
    }
    return pluginsUrls;
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
