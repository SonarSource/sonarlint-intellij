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
package org.sonarlint.intellij.util;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import java.util.HashMap;
import java.util.Map;

public enum SonarLintSeverity {
  BLOCKER(CodeInsightColors.WARNINGS_ATTRIBUTES, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, HighlightSeverity.WARNING),
  CRITICAL(CodeInsightColors.WARNINGS_ATTRIBUTES, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, HighlightSeverity.WARNING),
  MAJOR(CodeInsightColors.WARNINGS_ATTRIBUTES, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, HighlightSeverity.WARNING),
  MINOR(CodeInsightColors.WARNINGS_ATTRIBUTES, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, HighlightSeverity.WARNING),
  INFO(CodeInsightColors.WARNINGS_ATTRIBUTES, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, HighlightSeverity.WEAK_WARNING);

  private static final Map<String, SonarLintSeverity> cache;

  static {
    cache = new HashMap<>();
    for (SonarLintSeverity s : SonarLintSeverity.values()) {
      cache.put(s.toString(), s);
    }
  }

  private final TextAttributesKey defaultTextAttributes;
  private final ProblemHighlightType highlightType;
  private final HighlightSeverity highlightSeverity;

  SonarLintSeverity(TextAttributesKey defaultTextAttributes, ProblemHighlightType highlightType, HighlightSeverity highlightSeverity) {
    this.defaultTextAttributes = defaultTextAttributes;
    this.highlightType = highlightType;
    this.highlightSeverity = highlightSeverity;
  }

  public TextAttributesKey defaultTextAttributes() {
    return defaultTextAttributes;
  }

  public ProblemHighlightType highlightType() {
    return highlightType;
  }

  public HighlightSeverity highlightSeverity() {
    return highlightSeverity;
  }

  public static SonarLintSeverity byName(String name) {
    return cache.get(name);
  }
}
