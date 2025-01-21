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
package org.sonarlint.intellij.util;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;

public enum SonarLintSeverity {
  BLOCKER(CodeInsightColors.WARNINGS_ATTRIBUTES, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, HighlightSeverity.WARNING),
  HIGH(CodeInsightColors.WARNINGS_ATTRIBUTES, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, HighlightSeverity.WARNING),
  MEDIUM(CodeInsightColors.WARNINGS_ATTRIBUTES, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, HighlightSeverity.WARNING),
  LOW(CodeInsightColors.WARNINGS_ATTRIBUTES, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, HighlightSeverity.WARNING),
  INFO(CodeInsightColors.WARNINGS_ATTRIBUTES, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, HighlightSeverity.WEAK_WARNING),
  OLD_CODE(CodeInsightColors.WEAK_WARNING_ATTRIBUTES, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, HighlightSeverity.WEAK_WARNING);

  private static final Map<String, SonarLintSeverity> cache = Stream.of(values()).collect(Collectors.toMap(Enum::toString, Function.identity()));

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

  public static SonarLintSeverity fromCoreSeverity(@Nullable ImpactSeverity impact, IssueSeverity severity) {
    if (impact != null) {
      return cache.get(impact.toString());
    }
    return switch (severity) {
      case BLOCKER -> cache.get(ImpactSeverity.BLOCKER.toString());
      case CRITICAL -> cache.get(ImpactSeverity.HIGH.toString());
      case MINOR -> cache.get(ImpactSeverity.LOW.toString());
      case INFO -> cache.get(ImpactSeverity.INFO.toString());
      default -> cache.get(ImpactSeverity.MEDIUM.toString());
    };
  }
}
