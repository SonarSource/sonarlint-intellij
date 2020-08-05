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
package org.sonarlint.intellij.editor;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import org.jetbrains.annotations.NotNull;

public class SecondaryLocationIndexRenderer implements EditorCustomElementRenderer {
  private static final int RIGHT_MARGIN = 5;
  private static final int HORIZONTAL_PADDING = 6;
  private static final int VERTICAL_PADDING = 3;

  private static final Color BACKGROUND_COLOR = new Color(0xd1, 0x85, 0x82);
  private static final JBColor BACKGROUND_JB_COLOR = new JBColor(BACKGROUND_COLOR, BACKGROUND_COLOR);

  private static final Color SELECTED_BACKGROUND_COLOR = new Color(0xa4, 0x03, 0x0f);
  private static final JBColor SELECTED_BACKGROUND_JB_COLOR = new JBColor(SELECTED_BACKGROUND_COLOR, SELECTED_BACKGROUND_COLOR);

  private static final Color INDEX_COLOR = Color.WHITE;
  private static final JBColor INDEX_JB_COLOR = new JBColor(INDEX_COLOR, INDEX_COLOR);

  private final String index;
  private final boolean selected;

  public SecondaryLocationIndexRenderer(int index, boolean selected) {
    this.index = Integer.toString(index);
    this.selected = selected;
  }

  private static FontInfo getFontInfo(@NotNull Editor editor) {
    EditorColorsScheme colorsScheme = editor.getColorsScheme();
    FontPreferences fontPreferences = colorsScheme.getFontPreferences();
    return ComplementaryFontsRegistry.getFontAbleToDisplay('a', Font.PLAIN, fontPreferences,
      FontInfo.getFontRenderContext(editor.getContentComponent()));
  }

  @Override
  public int calcWidthInPixels(@NotNull Inlay inlay) {
    FontInfo fontInfo = getFontInfo(inlay.getEditor());
    return fontInfo.fontMetrics().stringWidth(index) + 2 * HORIZONTAL_PADDING + RIGHT_MARGIN;
  }

  @Override
  public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle targetRegion, @NotNull TextAttributes textAttributes) {
    g.setColor(selected ? SELECTED_BACKGROUND_JB_COLOR : BACKGROUND_JB_COLOR);
    g.fillRoundRect(targetRegion.x, targetRegion.y + VERTICAL_PADDING, targetRegion.width - RIGHT_MARGIN, targetRegion.height - 2 * VERTICAL_PADDING, 2, 2);
    FontInfo fontInfo = getFontInfo(inlay.getEditor());
    g.setFont(fontInfo.getFont());
    g.setColor(INDEX_JB_COLOR);
    g.drawString(index, targetRegion.x + HORIZONTAL_PADDING, targetRegion.y + fontInfo.fontMetrics().getAscent() + 1);
  }
}
