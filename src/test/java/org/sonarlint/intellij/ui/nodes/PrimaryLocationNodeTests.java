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
package org.sonarlint.intellij.ui.nodes;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.ui.SimpleTextAttributes;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.finding.Flow;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrimaryLocationNodeTests extends AbstractSonarLintLightTests {
  private PrimaryLocationNode node;
  private RangeMarker range = mock(RangeMarker.class);

  @BeforeEach
  void prepare() {
    myFixture.configureByText("file.txt", "my document test");

    when(range.isValid()).thenReturn(true);
    when(range.getStartOffset()).thenReturn(3);
    when(range.getEndOffset()).thenReturn(10);
  }

  @Test
  void testRenderer() {
    node = new PrimaryLocationNode(myFixture.getFile().getVirtualFile(), 3, range, "msg", new Flow(1, Collections.emptyList()));
    var renderer = mock(TreeCellRenderer.class);
    node.render(renderer);

    verify(renderer).append("(1, 3) ", SimpleTextAttributes.GRAY_ATTRIBUTES);
    verify(renderer).append("3:", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    verify(renderer).append("msg", SimpleTextAttributes.REGULAR_ATTRIBUTES);

  }

  @Test
  void testNoMessage() {
    node = new PrimaryLocationNode(myFixture.getFile().getVirtualFile(), 3, range, "...", new Flow(1, Collections.emptyList()));
    var renderer = mock(TreeCellRenderer.class);
    node.render(renderer);

    verify(renderer).append("(1, 3) ", SimpleTextAttributes.GRAY_ATTRIBUTES);
    verify(renderer).append("3:", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
  }

  @Test
  void testNoFile() {
    node = new PrimaryLocationNode(null, 3, range, "...", new Flow(1, Collections.emptyList()));
    var renderer = mock(TreeCellRenderer.class);
    node.render(renderer);

    verify(renderer).append("(-, -) ", SimpleTextAttributes.GRAY_ATTRIBUTES);
    verify(renderer).append("3:", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
  }
}
