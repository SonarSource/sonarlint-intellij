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
package org.sonarlint.intellij.ui.icons;

import java.awt.Component;
import java.awt.Graphics;
import javax.swing.Icon;

public class CompoundIcon implements Icon {
  public enum Axis {
    X_AXIS,
    Y_AXIS,
    Z_AXIS
  }

  public static final float TOP = 0.0f;
  public static final float LEFT = 0.0f;
  public static final float CENTER = 0.5f;
  public static final float BOTTOM = 1.0f;
  public static final float RIGHT = 1.0f;

  private final Icon[] icons;

  private final Axis axis;
  private final int gap;

  private float alignmentX = CENTER;
  private float alignmentY = CENTER;

  public CompoundIcon(Icon... icons) {
    this(Axis.X_AXIS, icons);
  }

  public CompoundIcon(Axis axis, Icon... icons) {
    this(axis, 0, icons);
  }

  public CompoundIcon(Axis axis, int gap, Icon... icons) {
    this(axis, gap, CENTER, CENTER, icons);
  }

  public CompoundIcon(Axis axis, int gap, float alignmentX, float alignmentY, Icon... icons) {
    this.axis = axis;
    this.gap = gap;

    if (alignmentX > 1.0f) {
      this.alignmentX = 1.0f;
    } else {
      this.alignmentX = Math.max(alignmentX, 0.0f);
    }
    if (alignmentY > 1.0f) {
      this.alignmentY = 1.0f;
    } else {
      this.alignmentY = Math.max(alignmentY, 0.0f);
    }
    for (var i = 0; i < icons.length; i++) {
      if (icons[i] == null) {
        var message = "Icon (" + i + ") cannot be null";
        throw new IllegalArgumentException(message);
      }
    }

    this.icons = icons;
  }

  public Axis getAxis() {
    return axis;
  }

  public int getGap() {
    return gap;
  }

  public float getAlignmentX() {
    return alignmentX;
  }

  public float getAlignmentY() {
    return alignmentY;
  }

  public int getIconCount() {
    return icons.length;
  }

  public Icon getIcon(int index) {
    return icons[index];
  }

  @Override
  public int getIconWidth() {
    var width = 0;

    if (axis == Axis.X_AXIS) {
      width += (icons.length - 1) * gap;

      for (Icon icon : icons) {
        width += icon.getIconWidth();
      }
    } else {
      for (Icon icon : icons) {
        width = Math.max(width, icon.getIconWidth());
      }
    }

    return width;
  }

  @Override
  public int getIconHeight() {
    var height = 0;

    if (axis == Axis.Y_AXIS) {
      height += (icons.length - 1) * gap;

      for (Icon icon : icons) {
        height += icon.getIconHeight();
      }
    } else {
      for (Icon icon : icons) {
        height = Math.max(height, icon.getIconHeight());
      }
    }

    return height;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int paramX, int paramY) {
    var x = paramX;
    var y = paramY;
    if (axis == Axis.X_AXIS) {
      var height = getIconHeight();

      for (var icon : icons) {
        var iconY = getOffset(height, icon.getIconHeight(), alignmentY);
        icon.paintIcon(c, g, x, y + iconY);
        x += icon.getIconWidth() + gap;
      }
    } else if (axis == Axis.Y_AXIS) {
      var width = getIconWidth();

      for (var icon : icons) {
        var iconX = getOffset(width, icon.getIconWidth(), alignmentX);
        icon.paintIcon(c, g, x + iconX, y);
        y += icon.getIconHeight() + gap;
      }
    } else {
      var width = getIconWidth();
      var height = getIconHeight();

      for (var icon : icons) {
        var iconX = getOffset(width, icon.getIconWidth(), alignmentX);
        var iconY = getOffset(height, icon.getIconHeight(), alignmentY);
        icon.paintIcon(c, g, x + iconX, y + iconY);
      }
    }
  }

  private static int getOffset(int maxValue, int iconValue, float alignment) {
    var offset = (maxValue - iconValue) * alignment;
    return Math.round(offset);
  }
}
