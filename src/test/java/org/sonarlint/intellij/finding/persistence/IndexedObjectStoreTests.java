/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
import java.util.List;
import java.util.Scanner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarsource.sonarlint.core.client.legacy.objectstore.PathMapper;
import org.sonarsource.sonarlint.core.client.legacy.objectstore.Reader;
import org.sonarsource.sonarlint.core.client.legacy.objectstore.Writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndexedObjectStoreTests extends AbstractSonarLintLightTests {
  private IndexedObjectStore<String, String> store;
  private StoreIndex<String> index = mock(StoreIndex.class);
  @TempDir
  Path root;
  private StoreKeyValidator<String> validator = mock(StoreKeyValidator.class);
  private Reader<String> reader;
  private PathMapper<String> mapper;
  private Writer<String> writer;

  @BeforeEach
  void before() {
    reader = (stream) -> new Scanner(stream).next();
    mapper = str -> root.resolve("a").resolve(str);
    writer = (stream, str) -> {
      try {
        stream.write(str.getBytes());
      } catch (IOException e) {
        e.printStackTrace();
      }
    };

    store = new IndexedObjectStore<>(index, mapper, reader, writer, validator);
  }

  @AfterEach
  void after() {
    getPath("mykey").getParent().toFile().setWritable(true);
  }

  private Path getPath(String key) {
    return root.resolve("a").resolve(key);
  }

  @Test
  void testWrite() throws IOException {
    store.write("mykey", "myvalue");
    assertThat(getPath("mykey")).hasContent("myvalue");
    verify(index).save("mykey", getPath("mykey"));

    assertThat(store.read("mykey")).contains("myvalue");
  }

  @Test
  void testContains() throws IOException {
    store.write("mykey", "myvalue");
    assertThat(getPath("mykey")).hasContent("myvalue");
    assertThat(store.contains("mykey")).isTrue();
    assertThat(store.contains("random")).isFalse();
  }

  @Test
  void testDelete() throws IOException {
    store.write("mykey", "myvalue");
    assertThat(getPath("mykey")).hasContent("myvalue");

    store.delete("mykey");
    assertThat(store.read("mykey")).isNotPresent();
    assertThat(getPath("mykey")).doesNotExist();
    verify(index).delete("mykey");
  }

  @Test
  void testErrorCleanInvalid() throws IOException {
    store.write("mykey", "myvalue");

    // Try to make file readonly on supported platform to prevent deletion
    if (!getPath("mykey").getParent().toFile().setReadOnly()) {
      // Fallback: replace file by a non empty directory to prevent deletion
      Files.delete(getPath("mykey"));
      Files.createDirectories(getPath("mykey").resolve("foo"));
    }

    when(index.keys()).thenReturn(List.of("mykey"));
    when(validator.apply(anyString())).thenReturn(Boolean.FALSE);

    store.deleteInvalid();
    assertThat(getPath("mykey")).exists();

    verify(index, never()).delete(anyString());
  }

  @Test
  void testClean() throws IOException {
    store.write("mykey", "myvalue");
    store.write("mykey2", "myvalue2");

    when(index.keys()).thenReturn(List.of("mykey", "mykey2"));
    when(validator.apply("mykey")).thenReturn(Boolean.FALSE);
    when(validator.apply("mykey2")).thenReturn(Boolean.TRUE);

    store.deleteInvalid();
    assertThat(getPath("mykey")).doesNotExist();
    assertThat(getPath("mykey2")).exists();
    verify(index).delete("mykey");
  }

  @Test
  void catchErrorsOnReadAndReturnEmpty() throws IOException {
    store.write("mykey", "myvalue");
    assertThat(store.read("mykey")).contains("myvalue");

    reader = (stream) -> {
      throw new IllegalStateException("Unable to read");
    };
    store = new IndexedObjectStore<>(index, mapper, reader, writer, validator);

    assertThat(store.read("mykey")).isEmpty();
  }
}
