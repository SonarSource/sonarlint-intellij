/**
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
package org.sonarlint.intellij.config;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;

import java.awt.Color;
import java.awt.Font;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public class SonarLintTextAttributes {
  public static final TextAttributesKey CRITICAL;
  public static final TextAttributesKey MAJOR;
  public static final TextAttributesKey MINOR;
  public static final TextAttributesKey BLOCKER;
  public static final TextAttributesKey INFO;

  static {
    // an alternative could be CodeInsightColors.*
    TextAttributes def = defaultTextAttributes();
    CRITICAL = createTextAttributesKey("SONARLINT_CRITICAL", def);
    MAJOR = createTextAttributesKey("SONARLINT_MAJOR", def);
    MINOR = createTextAttributesKey("SONARLINT_MINOR", def);
    BLOCKER = createTextAttributesKey("SONARLINT_BLOCKER", def);
    INFO = createTextAttributesKey("SONARLINT_INFO", def);
  }

  private SonarLintTextAttributes() {
    //only static
  }

  private static TextAttributes defaultTextAttributes() {
    Color c = JBColor.YELLOW.darker();
    TextAttributes attr = new TextAttributes(null, null, c, EffectType.WAVE_UNDERSCORE, Font.PLAIN);
    attr.setErrorStripeColor(c);
    return attr;
  }
}
