/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import javax.annotation.Nullable;
import org.sonarlint.intellij.config.SonarLintTextAttributes;
import org.sonarlint.intellij.util.SonarLintSeverity;
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;

/**
 * Maps SonarQube finding severities/impacts to the IntelliJ highlighting model (severity and text attributes).
 * Shared by {@link DirectHighlighter} to render findings directly in the editor.
 */
public final class SonarFindingHighlighting {

  private SonarFindingHighlighting() {
    // utility class
  }

  public static TextAttributesKey getTextAttrsKey(@Nullable ImpactSeverity impact, @Nullable IssueSeverity severity, boolean isOnNewCode,
    boolean isFocusOnNewCode) {
    if (isFocusOnNewCode && !isOnNewCode) {
      return SonarLintTextAttributes.OLD_CODE;
    }

    if (impact != null) {
      return switch (impact) {
        case BLOCKER -> SonarLintTextAttributes.BLOCKER;
        case HIGH -> SonarLintTextAttributes.HIGH;
        case LOW -> SonarLintTextAttributes.LOW;
        case INFO -> SonarLintTextAttributes.INFO;
        default -> SonarLintTextAttributes.MEDIUM;
      };
    }

    if (severity != null) {
      return switch (severity) {
        case BLOCKER -> SonarLintTextAttributes.BLOCKER;
        case CRITICAL -> SonarLintTextAttributes.HIGH;
        case MINOR -> SonarLintTextAttributes.LOW;
        case INFO -> SonarLintTextAttributes.INFO;
        default -> SonarLintTextAttributes.MEDIUM;
      };
    }

    return SonarLintTextAttributes.MEDIUM;
  }

  public static HighlightSeverity getSeverity(@Nullable ImpactSeverity impact, @Nullable IssueSeverity severity) {
    if (severity != null) {
      return SonarLintSeverity.fromCoreSeverity(impact, severity).highlightSeverity();
    }

    return HighlightSeverity.WARNING;
  }

}
