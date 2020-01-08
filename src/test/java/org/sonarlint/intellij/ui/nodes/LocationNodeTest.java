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

import com.intellij.mock.MockDocument;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.ui.SimpleTextAttributes;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LocationNodeTest {
  private LocationNode node;
  private RangeMarker range = mock(RangeMarker.class);

  @Before
  public void setUp() {
    MockDocument doc = new MockDocument();
    doc.replaceText("my document test", System.currentTimeMillis());
    when(range.getDocument()).thenReturn(doc);
    when(range.isValid()).thenReturn(true);
    when(range.getStartOffset()).thenReturn(3);
    when(range.getEndOffset()).thenReturn(10);
  }

  @Test
  public void testRenderer() {
    node = new LocationNode(3, range, "msg");
    TreeCellRenderer renderer = mock(TreeCellRenderer.class);
    node.render(renderer);

    verify(renderer).append("(1, 3) ", SimpleTextAttributes.GRAY_ATTRIBUTES, false);
    verify(renderer).append("3:", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    verify(renderer).append("msg");

  }

  @Test
  public void testNoMessage() {
    node = new LocationNode(3, range, "...");
    TreeCellRenderer renderer = mock(TreeCellRenderer.class);
    node.render(renderer);

    verify(renderer).append("(1, 3) ", SimpleTextAttributes.GRAY_ATTRIBUTES, false);
    verify(renderer).append("3:", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
  }
}
