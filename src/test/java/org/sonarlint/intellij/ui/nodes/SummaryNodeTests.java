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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.ui.tree.FindingTreeSummary;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;
import org.sonarlint.intellij.ui.tree.TreeContentKind;
import org.sonarlint.intellij.ui.tree.TreeSummary;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SummaryNodeTests extends AbstractSonarLintLightTests {
  private SummaryNode node;
  private SummaryNode nodeForSecurityHotspot;
  private TreeSummary treeSummary;
  private TreeSummary hotspotsTreeSummary;

  @BeforeEach
  void prepare() {
    treeSummary = new FindingTreeSummary(getProject(), TreeContentKind.ISSUES, false);
    node = new SummaryNode(treeSummary);
    hotspotsTreeSummary = new FindingTreeSummary(getProject(), TreeContentKind.SECURITY_HOTSPOTS, false);
    nodeForSecurityHotspot = new SummaryNode(hotspotsTreeSummary);
  }

  @Test
  void testTextIssue() {
    var child1 = mock(AbstractNode.class);
    treeSummary.refresh(1, 3);

    node.add(child1);

    var renderer = mock(TreeCellRenderer.class);
    node.render(renderer);

    verify(renderer).append("Found 3 issues");
  }

  @Test
  void testTextSecurityHotspot() {
    var child1 = mock(AbstractNode.class);
    hotspotsTreeSummary.refresh(1, 3);

    nodeForSecurityHotspot.add(child1);

    var renderer = mock(TreeCellRenderer.class);
    nodeForSecurityHotspot.render(renderer);

    verify(renderer).append("Found 3 Security Hotspots");
  }

  @Test
  void testNoIssues() {
    var renderer = mock(TreeCellRenderer.class);
    treeSummary.refresh(1, 0);
    node.render(renderer);

    verify(renderer).append("No issues to display");
  }

  @Test
  void testNoSecurityHotspots() {
    var renderer = mock(TreeCellRenderer.class);
    hotspotsTreeSummary.refresh(1, 0);
    nodeForSecurityHotspot.render(renderer);

    verify(renderer).append("No Security Hotspots to display");
  }

  @Test
  void testEmptyText() {
    var renderer = mock(TreeCellRenderer.class);
    node.render(renderer);

    verify(renderer).append("No analysis done");
  }
}
