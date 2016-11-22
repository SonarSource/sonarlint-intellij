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
package org.sonarlint.intellij.issue.persistence;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.commons.lang.SystemUtils;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;

public class StringStoreIndexTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException exception = ExpectedException.none();

  private Path baseDir;
  private StringStoreIndex index;

  @Before
  public void setUp() {
    index = new StringStoreIndex(temp.getRoot().toPath());
    baseDir = temp.getRoot().toPath();
  }

  @Test
  public void testSave() {
    Path test1 = baseDir.resolve("p1").resolve("file1");
    index.save("key1", test1);

    assertThat(baseDir.resolve(StringStoreIndex.INDEX_FILENAME)).exists();
    assertThat(index.keys()).containsOnly("key1");
  }

  @Test
  public void testDelete() {
    Path test1 = baseDir.resolve("p1").resolve("file1");
    index.save("key1", test1);
    index.delete("key1");
    index.delete("key2");

    assertThat(baseDir.resolve(StringStoreIndex.INDEX_FILENAME)).exists();
    assertThat(index.keys()).isEmpty();
  }

  @Test
  public void testErrorSave() throws IOException {
    // Setting folder readonly will not prevent to write in it on Windows
    Assume.assumeFalse(SystemUtils.IS_OS_WINDOWS);

    baseDir.toFile().setReadOnly();

    Path test1 = baseDir.resolve("p1").resolve("file1");
    exception.expect(IllegalStateException.class);
    index.save("key1", test1);
  }
}
