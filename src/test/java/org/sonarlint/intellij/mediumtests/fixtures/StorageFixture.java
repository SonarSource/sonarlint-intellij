/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
package org.sonarlint.intellij.mediumtests.fixtures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.serverconnection.proto.Sonarlint;
import org.sonarsource.sonarlint.core.serverconnection.storage.ProtobufUtil;

import static org.assertj.core.api.Fail.fail;
import static org.sonarsource.sonarlint.core.serverconnection.storage.ProjectStoragePaths.encodeForFs;

public class StorageFixture {
  public static StorageBuilder newStorage(String connectionId) {
    return new StorageBuilder(connectionId);
  }

  public static class Storage {
    private final Path path;
    private final List<Path> pluginPaths;
    private final List<ProjectStorageFixture.ProjectStorage> projectStorages;

    private Storage(Path path, List<Path> pluginPaths, List<ProjectStorageFixture.ProjectStorage> projectStorages) {
      this.path = path;
      this.pluginPaths = pluginPaths;
      this.projectStorages = projectStorages;
    }

    public Path getPath() {
      return path;
    }

    public List<Path> getPluginPaths() {
      return pluginPaths;
    }

    public List<ProjectStorageFixture.ProjectStorage> getProjectStorages() {
      return projectStorages;
    }
  }

  public static class StorageBuilder {
    private final String connectionId;
    private final List<Plugin> plugins = new ArrayList<>();
    private final List<ProjectStorageFixture.ProjectStorageBuilder> projectBuilders = new ArrayList<>();
    private String serverVersion;

    private StorageBuilder(String connectionId) {
      this.connectionId = connectionId;
    }

    public StorageBuilder withServerVersion(String serverVersion) {
      this.serverVersion = serverVersion;
      return this;
    }

    private StorageBuilder withPlugin(Path path, String jarName, String hash, String key) {
      plugins.add(new Plugin(path, jarName, hash, key));
      return this;
    }

    public StorageBuilder withProject(String projectKey, Consumer<ProjectStorageFixture.ProjectStorageBuilder> consumer) {
      var builder = new ProjectStorageFixture.ProjectStorageBuilder(projectKey);
      consumer.accept(builder);
      projectBuilders.add(builder);
      return this;
    }

    public StorageBuilder withProject(String projectKey) {
      projectBuilders.add(new ProjectStorageFixture.ProjectStorageBuilder(projectKey));
      return this;
    }

    public Storage create(Path rootPath) {
      var storagePath = rootPath.resolve("storage");
      var connectionStorage = storagePath.resolve(encodeForFs(connectionId));
      var pluginsFolderPath = connectionStorage.resolve("plugins");
      var projectsFolderPath = connectionStorage.resolve("projects");
      try {
        FileUtils.forceMkdir(pluginsFolderPath.toFile());
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }

      createServerInfo(connectionStorage);

      var pluginPaths = createPlugins(pluginsFolderPath);
      createPluginReferences(pluginsFolderPath);

      List<ProjectStorageFixture.ProjectStorage> projectStorages = new ArrayList<>();
      projectBuilders.forEach(project -> projectStorages.add(project.create(projectsFolderPath)));
      return new Storage(storagePath, pluginPaths, projectStorages);
    }

    private void createServerInfo(Path connectionStorage) {
      if (serverVersion != null) {
        ProtobufUtil.writeToFile(Sonarlint.ServerInfo.newBuilder().setVersion(serverVersion).build(), connectionStorage.resolve("server_info.pb"));
      }
    }

    private List<Path> createPlugins(Path pluginsFolderPath) {
      List<Path> pluginPaths = new ArrayList<>();
      plugins.forEach(plugin -> {
        var pluginPath = pluginsFolderPath.resolve(plugin.jarName);
        try {
          Files.copy(plugin.path, pluginPath);
        } catch (IOException e) {
          fail("Cannot copy plugin " + plugin.jarName, e);
        }
        pluginPaths.add(pluginPath);
      });
      return pluginPaths;
    }

    private void createPluginReferences(Path pluginsFolderPath) {
      var builder = Sonarlint.PluginReferences.newBuilder();
      plugins.forEach(plugin -> builder.putPluginsByKey(plugin.key, Sonarlint.PluginReferences.PluginReference.newBuilder()
        .setFilename(plugin.jarName)
        .setHash(plugin.hash)
        .setKey(plugin.key)
        .build()));
      ProtobufUtil.writeToFile(builder.build(), pluginsFolderPath.resolve("plugin_references.pb"));
    }

    private static class Plugin {
      private final Path path;
      private final String jarName;
      private final String hash;
      private final String key;

      private Plugin(Path path, String jarName, String hash, String key) {
        this.path = path;
        this.jarName = jarName;
        this.hash = hash;
        this.key = key;
      }
    }
  }
}
