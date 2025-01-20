/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
import java.util.Comparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.ui.nodes.AbstractNode;
import org.sonarlint.intellij.ui.nodes.FileNode;
import org.sonarlint.intellij.ui.nodes.SummaryNode;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;
import org.sonarlint.intellij.ui.tree.TreeContentKind;
import org.sonarlint.intellij.ui.tree.TreeSummary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractNodeTests extends AbstractSonarLintLightTests {
  private AbstractNode testNode;

  @BeforeEach
  void prepare() {
    testNode = new AbstractNode() {
      @Override public void render(TreeCellRenderer renderer) {
        // do nothing
      }
    };
  }

  @Test
  void testInsertion() {
    var summaryNode = new SummaryNode(new TreeSummary(getProject(), TreeContentKind.ISSUES, false));
    assertThat(summaryNode.insertFileNode(new FileNode(mockFile("name"), false), nameComparator)).isZero();
    assertThat(summaryNode.insertFileNode(new FileNode(mockFile("file"), false), nameComparator)).isZero();
    assertThat(summaryNode.insertFileNode(new FileNode(mockFile("test"), false), nameComparator)).isEqualTo(2);
    assertThat(summaryNode.insertFileNode(new FileNode(mockFile("abc"), false), nameComparator)).isZero();

    assertThat(summaryNode.getChildCount()).isEqualTo(4);
    assertThat(((FileNode) summaryNode.getChildAt(0)).file().getName()).isEqualTo("abc");
    assertThat(((FileNode) summaryNode.getChildAt(1)).file().getName()).isEqualTo("file");
    assertThat(((FileNode) summaryNode.getChildAt(2)).file().getName()).isEqualTo("name");
    assertThat(((FileNode) summaryNode.getChildAt(3)).file().getName()).isEqualTo("test");
  }

  @Test
  void testFileCount() {
    var child1 = mock(AbstractNode.class);
    var child2 = mock(AbstractNode.class);
    var child3 = mock(AbstractNode.class);

    when(child1.getFindingCount()).thenReturn(1);

    when(child2.getFindingCount()).thenReturn(2);

    when(child3.getFindingCount()).thenReturn(3);

    testNode.add(child1);
    testNode.add(child2);
    testNode.add(child3);

    assertThat(testNode.getFindingCount()).isEqualTo(6);
    assertThat(testNode.getFindingCount()).isEqualTo(6);

    //second call should be from cache
    verify(child1, times(1)).getFindingCount();
  }

  private final Comparator<FileNode> nameComparator = Comparator.comparing(f -> f.file().getName());

  private VirtualFile mockFile(String name) {
    var file = mock(VirtualFile.class);
    when(file.getName()).thenReturn(name);
    return file;
  }

}
