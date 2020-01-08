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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonarlint.intellij.ui.tree.TreeCellRenderer;

public class LocationNode extends AbstractNode {
  private final String message;
  private final Integer number;
  private final RangeMarker rangeMarker;
  private boolean bold = false;

  public LocationNode(RangeMarker rangeMarker, @Nullable String message) {
    this(null, rangeMarker, message);
  }

  public LocationNode(@Nullable Integer number, RangeMarker rangeMarker, @Nullable String message) {
    this.number = number;
    this.rangeMarker = rangeMarker;
    this.message = message;
  }

  public void setBold(boolean bold) {
    this.bold = bold;
  }

  public RangeMarker rangeMarker() {
    return rangeMarker;
  }

  @CheckForNull
  public String message() {
    return message;
  }

  @Override public void render(TreeCellRenderer renderer) {
    renderer.setIpad(JBUI.insets(3, 3, 3, 3));
    renderer.setBorder(null);
    renderer.append(issueCoordinates(), bold ? SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES : SimpleTextAttributes.GRAY_ATTRIBUTES);
    if (number != null) {
      renderer.append(String.valueOf(number) + ":", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }
    renderer.append("  ");
    if (message != null && !message.isEmpty() && !"...".equals(message)) {
      renderer.append(message, bold ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
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
