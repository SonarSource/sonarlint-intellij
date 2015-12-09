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
package org.sonarlint.intellij;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.editor.SonarExternalAnnotator;

public class SonarApplication implements ApplicationComponent {
  private IdeaPluginDescriptor plugin;

  @Override
  public void initComponent() {
    plugin = PluginManager.getPlugin(PluginId.getId("org.sonarlint.idea"));
    for (Language language : Language.getRegisteredLanguages()) {
      registerExternalAnnotatorFor(language);
    }
  }

  private void registerExternalAnnotatorFor(Language language) {
    LanguageExtensionPoint<SonarExternalAnnotator> ep = new LanguageExtensionPoint<>();
    ep.language = language.getID();
    ep.implementationClass = SonarExternalAnnotator.class.getName();
    ep.setPluginDescriptor(plugin);
    Extensions.getRootArea().getExtensionPoint("com.intellij.externalAnnotator").registerExtension(ep);
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
