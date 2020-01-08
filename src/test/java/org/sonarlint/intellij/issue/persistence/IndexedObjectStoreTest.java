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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Scanner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.PathMapper;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.Reader;
import org.sonarsource.sonarlint.core.client.api.connected.objectstore.Writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IndexedObjectStoreTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private IndexedObjectStore<String, String> store;
  private StoreIndex<String> index = mock(StoreIndex.class);
  private Path root;
  private StoreKeyValidator<String> validator = mock(StoreKeyValidator.class);

  @Before
  public void setUp() {
    root = temp.getRoot().toPath();
    PathMapper<String> mapper = str -> root.resolve("a").resolve(str);
    Reader<String> reader = (stream) -> new Scanner(stream).next();
    Writer<String> writer = (stream, str) -> {
      try {
        stream.write(str.getBytes());
      } catch (IOException e) {
        e.printStackTrace();
      }
    };

    store = new IndexedObjectStore<>(index, mapper, reader, writer, validator);
  }

  private Path getPath(String key) {
    return root.resolve("a").resolve(key);
  }

  @Test
  public void testWrite() throws IOException {
    store.write("mykey", "myvalue");
    assertThat(getPath("mykey")).hasContent("myvalue");
    verify(index).save("mykey", getPath("mykey"));

    assertThat(store.read("mykey").get()).isEqualTo("myvalue");
  }

  @Test
  public void testContains() throws IOException {
    store.write("mykey", "myvalue");
    assertThat(getPath("mykey")).hasContent("myvalue");
    assertThat(store.contains("mykey")).isTrue();
    assertThat(store.contains("random")).isFalse();
  }

  @Test
  public void testDelete() throws IOException {
    store.write("mykey", "myvalue");
    assertThat(getPath("mykey")).hasContent("myvalue");

    store.delete("mykey");
    assertThat(store.read("mykey").isPresent()).isFalse();
    assertThat(getPath("mykey")).doesNotExist();
    verify(index).delete("mykey");
  }

  @Test
  public void testErrorCleanInvalid() throws IOException {
    store.write("mykey", "myvalue");

    // Try to make file readonly on supported platform to prevent deletion
    if (!getPath("mykey").getParent().toFile().setReadOnly()) {
      // Fallback: replace file by a non empty directory to prevent deletion
      Files.delete(getPath("mykey"));
      Files.createDirectories(getPath("mykey").resolve("foo"));
    }

    when(index.keys()).thenReturn(Collections.singletonList("mykey"));
    when(validator.apply(anyString())).thenReturn(Boolean.FALSE);

    store.deleteInvalid();
    assertThat(getPath("mykey")).exists();

    verify(index, never()).delete(anyString());
  }

  @Test
  public void testErrorOnDeleteInvalid() {
    when(index.keys()).thenThrow(new IllegalStateException("error"));
    store.deleteInvalid();
  }

  @Test
  public void testClean() throws IOException {
    store.write("mykey", "myvalue");
    store.write("mykey2", "myvalue2");

    when(index.keys()).thenReturn(Arrays.asList("mykey", "mykey2"));
    when(validator.apply("mykey")).thenReturn(Boolean.FALSE);
    when(validator.apply("mykey2")).thenReturn(Boolean.TRUE);

    store.deleteInvalid();
    assertThat(getPath("mykey")).doesNotExist();
    assertThat(getPath("mykey2")).exists();
    verify(index).delete("mykey");
  }
}
