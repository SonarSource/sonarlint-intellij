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
package org.sonarlint.intellij.config;

import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.sonarlint.intellij.util.SonarLintSeverity;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public class SonarLintTextAttributes {
  public static final TextAttributesKey BLOCKER;
  public static final TextAttributesKey HIGH;
  public static final TextAttributesKey MEDIUM;
  public static final TextAttributesKey LOW;
  public static final TextAttributesKey INFO;
  public static final TextAttributesKey OLD_CODE;
  public static final TextAttributesKey SELECTED;

  public static final TextAttributesKey DIFF_ADDITION;
  public static final TextAttributesKey DIFF_REMOVAL;

  static {
    /*
     * Defaults should be consistent with SonarLintSeverity
     */
    BLOCKER = createTextAttributesKey("SONARLINT_BLOCKER", SonarLintSeverity.BLOCKER.defaultTextAttributes());
    HIGH = createTextAttributesKey("SONARLINT_HIGH", SonarLintSeverity.HIGH.defaultTextAttributes());
    MEDIUM = createTextAttributesKey("SONARLINT_MEDIUM", SonarLintSeverity.MEDIUM.defaultTextAttributes());
    LOW = createTextAttributesKey("SONARLINT_LOW", SonarLintSeverity.LOW.defaultTextAttributes());
    INFO = createTextAttributesKey("SONARLINT_INFO", SonarLintSeverity.INFO.defaultTextAttributes());
    OLD_CODE = createTextAttributesKey("SONARLINT_OLD_CODE", SonarLintSeverity.OLD_CODE.defaultTextAttributes());
    SELECTED = createTextAttributesKey("SONARLINT_SELECTED");
    DIFF_ADDITION = createTextAttributesKey("SONARLINT_DIFF_ADDITION", DiffColors.DIFF_INSERTED);
    DIFF_REMOVAL = createTextAttributesKey("SONARLINT_DIFF_REMOVAL", DiffColors.DIFF_DELETED);
  }

  private SonarLintTextAttributes() {
    //only static
  }

}
