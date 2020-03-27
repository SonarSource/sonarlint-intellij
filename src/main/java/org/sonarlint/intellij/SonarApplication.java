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
package org.sonarlint.intellij;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.core.AnalysisRequirementNotifications;
import org.sonarlint.intellij.core.SonarLintProjectNotifications;
import org.sonarlint.intellij.core.SonarQubeEventNotifications;
import org.sonarlint.intellij.editor.SonarExternalAnnotator;
import org.sonarsource.sonarlint.core.client.api.util.FileUtils;

public class SonarApplication extends ApplicationComponent.Adapter {
  private IdeaPluginDescriptor plugin;
  private ConcurrentMap<String, LanguageExtensionPoint> annotatorsByLanguage;

  @Override
  public void initComponent() {
    plugin = PluginManager.getPlugin(PluginId.getId("org.sonarlint.idea"));
    annotatorsByLanguage = new ConcurrentHashMap<>();
    registerExternalAnnotator();
    registerNotifications();
    cleanOldWorkDir();
  }

  public void registerExternalAnnotator() {
    Language.getRegisteredLanguages().stream()
      .filter(SonarApplication::doesNotImplementMetaLanguage)
      .filter(SonarApplication::doesNotHaveBaseLanguage)
      .filter(language -> !(annotatorsByLanguage.containsKey(language.getID())))
      .forEach(this::registerExternalAnnotatorFor);

    deregisterRemovedLanguages();
  }

  private static boolean doesNotImplementMetaLanguage(Language lang) {
    Class<?> superclass = lang.getClass().getSuperclass();
    while (superclass != null) {
      if ("com.intellij.lang.MetaLanguage".equals(superclass.getName())) {
        return false;
      }
      superclass = superclass.getSuperclass();
    }
    return true;
  }

  private static boolean doesNotHaveBaseLanguage(Language lang) {
    return lang.getBaseLanguage() == null;
  }

  private void deregisterRemovedLanguages() {
    Set<String> toRemove = annotatorsByLanguage.keySet()
      .stream()
      .filter(id -> Language.findLanguageByID(id) == null)
      .collect(Collectors.toSet());

    toRemove.forEach(l -> {
      getExternalAnnotatorExtensionPoint().unregisterExtension(annotatorsByLanguage.get(l));
      annotatorsByLanguage.remove(l);
    });
  }

  @NotNull
  private static ExtensionPoint<Object> getExternalAnnotatorExtensionPoint() {
    return Extensions.getRootArea().getExtensionPoint("com.intellij.externalAnnotator");
  }

  public String getVersion() {
    return plugin.getVersion();
  }

  private void registerExternalAnnotatorFor(Language language) {
    LanguageExtensionPoint<SonarExternalAnnotator> ep = new LanguageExtensionPoint<>();
    ep.language = language.getID();
    ep.implementationClass = SonarExternalAnnotator.class.getName();
    ep.setPluginDescriptor(plugin);
    getExternalAnnotatorExtensionPoint().registerExtension(ep);
    annotatorsByLanguage.put(language.getID(), ep);
  }

  private static void registerNotifications() {
    NotificationGroup.balloonGroup(AnalysisRequirementNotifications.GROUP_ANALYSIS_PROBLEM);
    NotificationGroup.balloonGroup(SonarLintProjectNotifications.GROUP_BINDING_PROBLEM);
    NotificationGroup.balloonGroup(SonarLintProjectNotifications.GROUP_UPDATE_NOTIFICATION);
    NotificationGroup.balloonGroup(SonarQubeEventNotifications.GROUP_SONARQUBE_EVENT);
  }

  @Override
  public void disposeComponent() {
    // nothing to do
  }

  @NotNull
  @Override
  public String getComponentName() {
    return getClass().getSimpleName();
  }

  private static void cleanOldWorkDir() {
    Path oldWorkDir = Paths.get(PathManager.getConfigPath()).resolve("sonarlint").resolve("work");
    if (!Files.isDirectory(oldWorkDir)) {
      return;
    }

    try (Stream<Path> stream = Files.list(oldWorkDir)) {
      stream.filter(f -> f.getFileName().toString().startsWith(".sonartmp_"))
        .forEach(FileUtils::deleteRecursively);
    } catch (IOException e) {
      // ignore
    }
  }

  public Path getPluginPath() {
    return plugin.getPath().toPath();
  }
}
