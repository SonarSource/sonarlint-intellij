/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.PluginDescriptor;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.APP)
public final class SonarLintPlugin {
  private PluginDescriptor plugin;

  public String getVersion() {
    return getPlugin().getVersion();
  }

  public Path getPath() {
    return getPlugin().getPluginPath();
  }

  private @NotNull PluginDescriptor getPlugin() {
    if (plugin == null) {
      plugin = PluginManager.getPluginByClass(SonarLintPlugin.class);
      if (plugin == null) {
        throw new IllegalStateException("Cannot find SonarLint plugin descriptor");
      }
    }
    return plugin;
  }
}
