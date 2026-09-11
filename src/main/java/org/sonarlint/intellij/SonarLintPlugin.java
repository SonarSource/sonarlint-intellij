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

import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.PluginDescriptor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.APP)
public final class SonarLintPlugin {

  private static final String TEST_VERSION = "test";

  private @Nullable PluginDescriptor plugin;

  public String getVersion() {
    var descriptor = resolveDescriptor();
    if (descriptor != null) {
      return descriptor.getVersion();
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return TEST_VERSION;
    }
    throw new IllegalStateException("Cannot find SonarLint plugin descriptor");
  }

  public Path getPath() {
    var descriptor = resolveDescriptor();
    if (descriptor != null) {
      return descriptor.getPluginPath();
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return resolvePathFromTestPluginsDir();
    }
    throw new IllegalStateException("Cannot find SonarLint plugin path");
  }

  private @Nullable PluginDescriptor resolveDescriptor() {
    if (plugin == null) {
      var classLoader = getClass().getClassLoader();
      if (classLoader instanceof PluginAwareClassLoader pluginClassLoader) {
        plugin = pluginClassLoader.getPluginDescriptor();
      }
    }
    return plugin;
  }

  /**
   * In unit tests the plugin classes are not loaded by {@link PluginAwareClassLoader},
   * but the plugin is still installed under the test sandbox plugins directory.
   */
  private static Path resolvePathFromTestPluginsDir() {
    var pluginsPath = Paths.get(PathManager.getPluginsPath());
    try (var stream = Files.list(pluginsPath)) {
      return stream
        .filter(Files::isDirectory)
        .filter(path -> Files.isDirectory(path.resolve("sloop")))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Cannot find SonarLint plugin path in test sandbox: " + pluginsPath));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot list test plugins directory: " + pluginsPath, e);
    }
  }
}
