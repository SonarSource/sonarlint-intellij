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
package org.sonarlint.intellij.issue.persistence;

import com.intellij.openapi.diagnostic.Logger;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.ObjectStore;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.PathMapper;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.Reader;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.Writer;

/**
 * An ObjectStore without internal cache that derives the filesystem path to storage using a provided PathMapper.
 *
 * @param <K> type of the key to store by and used when reading back; must be hashable
 * @param <V> type of the value to store
 */
class IndexedObjectStore<K, V> implements ObjectStore<K, V> {
  private static final Logger LOGGER = Logger.getInstance(IndexedObjectStore.class);
  private final StoreIndex<K> index;
  private final PathMapper<K> pathMapper;
  private final Reader<V> reader;
  private final Writer<V> writer;
  private final StoreKeyValidator<K> validator;

  IndexedObjectStore(StoreIndex<K> index, PathMapper<K> pathMapper, Reader<V> reader, Writer<V> writer, StoreKeyValidator<K> validator) {
    this.index = index;
    this.pathMapper = pathMapper;
    this.reader = reader;
    this.writer = writer;
    this.validator = validator;
  }

  @Override
  public Optional<V> read(K key) throws IOException {
    Path path = pathMapper.apply(key);
    if (!path.toFile().exists()) {
      return Optional.empty();
    }
    try (InputStream is = new BufferedInputStream(Files.newInputStream(path))) {
      return Optional.of(reader.apply(is));
    }
  }

  public boolean contains(K key) {
    Path path = pathMapper.apply(key);
    return path.toFile().exists();
  }

  /**
   * Deletes all entries in the index are no longer valid.
   */
  public void deleteInvalid() {
    int counter = 0;
    Collection<K> keys;
    try {
      keys = index.keys();
    } catch (Exception e) {
      LOGGER.warn("Failed to read the store", e);
      return;
    }

    for (K k : keys) {
      if (!validator.apply(k)) {
        try {
          counter++;
          delete(k);
        } catch (IOException e) {
          LOGGER.warn("Failed to delete file in the store", e);
        }
      }
    }
    LOGGER.debug(String.format("%d entries removed from the store", counter));
  }

  @Override
  public void delete(K key) throws IOException {
    Path path = pathMapper.apply(key);
    Files.deleteIfExists(path);
    index.delete(key);
  }

  @Override
  public void write(K key, V value) throws IOException {
    Path path = pathMapper.apply(key);
    index.save(key, path);
    Path parent = path.getParent();
    if (!parent.toFile().exists()) {
      Files.createDirectories(parent);
    }
    try (OutputStream out = Files.newOutputStream(path)) {
      writer.accept(out, value);
    }
  }
}
