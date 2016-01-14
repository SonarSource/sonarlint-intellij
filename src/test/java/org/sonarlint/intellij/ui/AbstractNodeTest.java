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
package org.sonarlint.intellij.ui;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.ui.nodes.AbstractNode;
import org.sonarlint.intellij.ui.nodes.FileNode;

import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbstractNodeTest {
  private AbstractNode testNode;

  @Before
  public void setUp() {
    testNode = new AbstractNode() {
      @Override public void render(ColoredTreeCellRenderer renderer) {
        // do nothing
      }
    };
  }

  @Test
  public void testInsertion() {
    assertThat(testNode.getInsertIdx(new FileNode(mockFile("name")), nameComparator)).isEqualTo(0);
    assertThat(testNode.getInsertIdx(new FileNode(mockFile("file")), nameComparator)).isEqualTo(0);
    assertThat(testNode.getInsertIdx(new FileNode(mockFile("test")), nameComparator)).isEqualTo(2);
    assertThat(testNode.getInsertIdx(new FileNode(mockFile("abc")), nameComparator)).isEqualTo(0);

    assertThat(testNode.getChildCount()).isEqualTo(4);
    assertThat(((FileNode) testNode.getChildAt(0)).file().getName()).isEqualTo("abc");
    assertThat(((FileNode) testNode.getChildAt(1)).file().getName()).isEqualTo("file");
    assertThat(((FileNode) testNode.getChildAt(2)).file().getName()).isEqualTo("name");
    assertThat(((FileNode) testNode.getChildAt(3)).file().getName()).isEqualTo("test");

  }

  private Comparator<FileNode> nameComparator = new Comparator<FileNode>() {
    @Override public int compare(FileNode f1, FileNode f2) {
      return f1.file().getName().compareTo(f2.file().getName());
    }
  };

  private VirtualFile mockFile(String name) {
    VirtualFile file = mock(VirtualFile.class);
    when(file.getName()).thenReturn(name);
    return file;
  }


}
