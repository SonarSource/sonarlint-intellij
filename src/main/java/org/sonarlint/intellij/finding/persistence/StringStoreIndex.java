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
package org.sonarlint.intellij.finding.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.sonarlint.intellij.proto.Sonarlint;
import org.sonarlint.intellij.util.GlobalLogOutput;

public class StringStoreIndex implements StoreIndex<String> {
  public static final String INDEX_FILENAME = "index.pb";
  private final Path storeBasePath;
  private final Path indexFilePath;

  public StringStoreIndex(Path storeBasePath) {
    this.storeBasePath = storeBasePath;
    this.indexFilePath = storeBasePath.resolve(INDEX_FILENAME);
  }

  @Override
  public synchronized Collection<String> keys() {
    return load().keySet();
  }

  private Map<String, String> load() {
    if (!indexFilePath.toFile().exists()) {
      return Collections.emptyMap();
    }
    try (var stream = Files.newInputStream(indexFilePath)) {
      return Sonarlint.StorageIndex.parseFrom(stream).getMappedPathByKeyMap();
    } catch (IOException e) {
      GlobalLogOutput.get().logError("Unable to read SonarLint issue store.", e);
      return Collections.emptyMap();
    }
  }

  @Override
  public synchronized void save(String storageKey, Path path) {
    var relativeMappedPath = storeBasePath.relativize(path).toString();
    var builder = Sonarlint.StorageIndex.newBuilder();
    builder.putAllMappedPathByKey(load());
    builder.putMappedPathByKey(storageKey, relativeMappedPath);
    save(builder.build());
  }

  @Override
  public synchronized void delete(String storageKey) {
    var builder = Sonarlint.StorageIndex.newBuilder();
    builder.putAllMappedPathByKey(load());
    builder.removeMappedPathByKey(storageKey);
    save(builder.build());
  }

  private void save(Sonarlint.StorageIndex index) {
    try (var stream = Files.newOutputStream(indexFilePath)) {
      index.writeTo(stream);
    } catch (IOException e) {
      // Don't log in the SonarLint console as the problem can occurs when stopping the IDE
      GlobalLogOutput.get().logError("Unable to write SonarLint issue store.", e);
    }
  }
}
