/*
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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import java.awt.Insets;
import javax.annotation.Nullable;

public class LocationNode extends AbstractNode {
  private String message;
  private final int number;
  private RangeMarker rangeMarker;

  public LocationNode(int number, RangeMarker rangeMarker, @Nullable String message) {
    this.number = number;
    this.rangeMarker = rangeMarker;
    this.message = message;
  }

  public RangeMarker rangeMarker() {
    return rangeMarker;
  }

  public String message() {
    return message;
  }

  @Override public void render(ColoredTreeCellRenderer renderer) {
    renderer.setIpad(new Insets(3, 3, 3, 3));
    renderer.setBorder(null);
    renderer.append(issueCoordinates(), SimpleTextAttributes.GRAY_ATTRIBUTES);
    renderer.append(String.valueOf(number) + ":", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    renderer.append("  ");
    if (message != null && !message.isEmpty() && !"...".equals(message)) {
      renderer.append(message);
    } else {
      renderer.append("[ no message ]");
    }
  }

  private String issueCoordinates() {
    if (rangeMarker == null) {
      return "(0, 0) ";
    }

    if (!rangeMarker.isValid()) {
      return "(-, -) ";
    }

    Document doc = rangeMarker.getDocument();
    int line = doc.getLineNumber(rangeMarker.getStartOffset());
    int offset = rangeMarker.getStartOffset() - doc.getLineStartOffset(line);
    return String.format("(%d, %d) ", line + 1, offset);
  }
}
