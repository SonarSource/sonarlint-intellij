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
package org.sonarlint.intellij.ui.nodes;

import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SummaryNodeTests {
  private final SummaryNode node = new SummaryNode();
  private final SummaryNode nodeForSecurityHotspot = new SummaryNode(true);

  @Test
  void testTextIssue() {
    var child1 = mock(AbstractNode.class);
    when(child1.getFindingCount()).thenReturn(3);

    node.add(child1);

    var renderer = mock(TreeCellRenderer.class);
    node.render(renderer);

    verify(renderer).append("Found 3 issues in 1 file");
  }

  @Test
  void testTextSecurityHotspot() {
    var child1 = mock(AbstractNode.class);
    when(child1.getFindingCount()).thenReturn(3);

    nodeForSecurityHotspot.add(child1);

    var renderer = mock(TreeCellRenderer.class);
    nodeForSecurityHotspot.render(renderer);

    verify(renderer).append("Found 3 security hotspots in 1 file");
  }

  @Test
  void testNoIssues() {
    var renderer = mock(TreeCellRenderer.class);
    node.render(renderer);

    verify(renderer).append("No issues to display");
  }

  @Test
  void testNoSecurityHotspots() {
    var renderer = mock(TreeCellRenderer.class);
    nodeForSecurityHotspot.render(renderer);

    verify(renderer).append("No security hotspots to display");
  }

  @Test
  void testEmptyText() {
    var renderer = mock(TreeCellRenderer.class);
    node.setEmptyText("Empty");
    node.render(renderer);

    verify(renderer).append("Empty");
  }
}
