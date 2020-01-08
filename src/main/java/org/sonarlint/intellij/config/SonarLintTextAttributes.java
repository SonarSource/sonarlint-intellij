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
package org.sonarlint.intellij.config;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.sonarlint.intellij.util.SonarLintSeverity;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public class SonarLintTextAttributes {
  public static final TextAttributesKey CRITICAL;
  public static final TextAttributesKey MAJOR;
  public static final TextAttributesKey MINOR;
  public static final TextAttributesKey BLOCKER;
  public static final TextAttributesKey INFO;
  public static final TextAttributesKey SELECTED;

  static {
    /*
     * Defaults should be consistent with SonarLintSeverity
     */
    CRITICAL = createTextAttributesKey("SONARLINT_CRITICAL", SonarLintSeverity.CRITICAL.defaultTextAttributes());
    MAJOR = createTextAttributesKey("SONARLINT_MAJOR", SonarLintSeverity.MAJOR.defaultTextAttributes());
    MINOR = createTextAttributesKey("SONARLINT_MINOR", SonarLintSeverity.MINOR.defaultTextAttributes());
    INFO = createTextAttributesKey("SONARLINT_INFO", SonarLintSeverity.INFO.defaultTextAttributes());
    BLOCKER = createTextAttributesKey("SONARLINT_BLOCKER", SonarLintSeverity.BLOCKER.defaultTextAttributes());
    SELECTED = createTextAttributesKey("SONARLINT_SELECTED");
  }

  private SonarLintTextAttributes() {
    //only static
  }

}
