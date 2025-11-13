/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileNodeTests {
  private final VirtualFile file = mock(VirtualFile.class);
  private final FileNode node = new FileNode(file, false);

  @BeforeEach
  void setUp() {
    var type = mock(FileType.class);
    when(type.getIcon()).thenReturn(AllIcons.FileTypes.Java);

    when(file.getFileType()).thenReturn(type);
    when(file.getName()).thenReturn("fileName");
  }

  @Test
  void testCountIssues() {
    node.add(createTestIssueNode());
    node.add(createTestIssueNode());
    assertThat(node.getFindingCount()).isEqualTo(2);
  }

  @Test
  void testGetFile() {
    assertThat(node.file()).isEqualTo(file);
  }

  private static IssueNode createTestIssueNode() {
    return mock(IssueNode.class);
  }
}
