/*
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
package org.sonarlint.intellij;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginId;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.core.SonarLintProjectNotifications;
import org.sonarlint.intellij.editor.SonarExternalAnnotator;

public class SonarApplication implements ApplicationComponent {
  private static final Set<String> languages = new HashSet<>();

  static {
    languages.add("Groovy");
    languages.add("XML");
    languages.add("XHTML");
    languages.add("JAVA");
    languages.add("JavaScript");
    languages.add("TEXT");

    languages.add("PostgresPLSQL");
    languages.add("SQL");
    languages.add("SQLite");
    languages.add("Sybase");
    languages.add("H2");
    languages.add("SQL92");
    languages.add("Oracle");
    languages.add("OracleSqlPlus");

    languages.add("Python");
    languages.add("Cobol");
    languages.add("COBOL");
    languages.add("Swift");
    languages.add("SWIFT");
    languages.add("PHP");
  }

  private IdeaPluginDescriptor plugin;

  @Override
  public void initComponent() {
    plugin = PluginManager.getPlugin(PluginId.getId("org.sonarlint.idea"));
    Language.getRegisteredLanguages().stream()
      .filter(lang -> languages.contains(lang.getID()))
      .forEach(this::registerExternalAnnotatorFor);
    registerNotifications();
  }

  public String getVersion() {
    return plugin.getVersion();
  }

  private void registerExternalAnnotatorFor(Language language) {
    LanguageExtensionPoint<SonarExternalAnnotator> ep = new LanguageExtensionPoint<>();
    ep.language = language.getID();
    ep.implementationClass = SonarExternalAnnotator.class.getName();
    ep.setPluginDescriptor(plugin);
    Extensions.getRootArea().getExtensionPoint("com.intellij.externalAnnotator").registerExtension(ep);
  }

  public void registerNotifications() {
    NotificationGroup.balloonGroup(SonarLintProjectNotifications.GROUP_BINDING_PROBLEM);
    NotificationGroup.balloonGroup(SonarLintProjectNotifications.GROUP_UPDATE_NOTIFICATION);
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
}
