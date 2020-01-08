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
package org.sonarlint.intellij.ui.nodes;

import org.junit.Test;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SummaryNodeTest {
  private SummaryNode node = new SummaryNode();

  @Test
  public void testText() {
    AbstractNode child1 = mock(AbstractNode.class);
    when(child1.getIssueCount()).thenReturn(3);

    node.add(child1);

    TreeCellRenderer renderer = mock(TreeCellRenderer.class);
    node.render(renderer);

    verify(renderer).append("Found 3 issues in 1 file");
  }

  @Test
  public void testNoIssues() {
    TreeCellRenderer renderer = mock(TreeCellRenderer.class);
    node.render(renderer);

    verify(renderer).append("No issues to display");
  }

  @Test
  public void testEmptyText() {
    TreeCellRenderer renderer = mock(TreeCellRenderer.class);
    node.setEmptyText("Empty");
    node.render(renderer);

    verify(renderer).append("Empty");
  }
}
