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
package org.sonarlint.intellij.ui.tree;

import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.ui.nodes.FileNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IssueTreeIndexTest {
  private IssueTreeIndex idx = new IssueTreeIndex();

  private VirtualFile file1;
  private VirtualFile file2;

  private FileNode node1;
  private FileNode node2;

  @Before
  public void setUp() {
    file1 = createFile("file1");
    file2 = createFile("file1");
    node1 = new FileNode(file1);
    node2 = new FileNode(file2);
  }

  @Test
  public void testClear() {
    idx.setFileNode(node1);
    idx.setFileNode(node2);
    assertThat(idx.getAllFiles()).isNotEmpty();

    idx.clear();
    assertThat(idx.getAllFiles()).isEmpty();
  }

  @Test
  public void testRemove() {
    idx.setFileNode(node1);
    assertThat(idx.getAllFiles()).isNotEmpty();

    idx.remove(file1);
    assertThat(idx.getAllFiles()).isEmpty();
  }

  @Test
  public void testIndex() {
    idx.setFileNode(node1);
    idx.setFileNode(node2);

    assertThat(idx.getFileNode(file1)).isEqualTo(node1);
    assertThat(idx.getFileNode(createFile("file1"))).isNull();

    assertThat(idx.getAllFiles()).containsOnly(file1, file2);
  }

  private VirtualFile createFile(String name) {
    VirtualFile newFile = mock(VirtualFile.class);
    when(newFile.getName()).thenReturn(name);
    return newFile;
  }
}
