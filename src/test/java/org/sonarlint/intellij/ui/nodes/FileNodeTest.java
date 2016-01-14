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
package org.sonarlint.intellij.ui.nodes;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FileNodeTest {
  private VirtualFile file;
  private FileNode node;

  @Before
  public void setUp() {
    file = mock(VirtualFile.class);
    FileType type = mock(FileType.class);
    when(type.getIcon()).thenReturn(AllIcons.FileTypes.Java);

    when(file.getFileType()).thenReturn(type);
    when(file.getName()).thenReturn("fileName");
    node = new FileNode(file);
  }

  @Test
  public void testCountFile() {
    assertThat(node.getFileCount()).isEqualTo(1);
  }

  @Test
  public void testCountIssues() {
    node.add(createTestIssueNode());
    node.add(createTestIssueNode());
    assertThat(node.getIssueCount()).isEqualTo(2);
    assertThat(node.getFileCount()).isEqualTo(1);
  }

  @Test
  public void testGetFile() {
    assertThat(node.file()).isEqualTo(file);
  }

  @Test
  public void testRender() {
    node.add(createTestIssueNode());
    ColoredTreeCellRenderer renderer = mock(ColoredTreeCellRenderer.class);
    node.render(renderer);

    verify(renderer).append("fileName");
    verify(renderer).setIcon(AllIcons.FileTypes.Java);
  }

  private static IssueNode createTestIssueNode() {
    return mock(IssueNode.class);
  }
}
