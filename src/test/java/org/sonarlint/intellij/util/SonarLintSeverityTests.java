/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;

import static org.assertj.core.api.Assertions.assertThat;

class SonarLintSeverityTests {
  @Test
  void testSeveritiesExist() {
    assertThat(SonarLintSeverity.fromCoreSeverity(null, IssueSeverity.BLOCKER)).isNotNull();
    assertThat(SonarLintSeverity.fromCoreSeverity(null, IssueSeverity.MAJOR)).isNotNull();
    assertThat(SonarLintSeverity.fromCoreSeverity(null, IssueSeverity.MINOR)).isNotNull();
    assertThat(SonarLintSeverity.fromCoreSeverity(null, IssueSeverity.INFO)).isNotNull();
    assertThat(SonarLintSeverity.fromCoreSeverity(null, IssueSeverity.CRITICAL)).isNotNull();

    assertThat(SonarLintSeverity.fromCoreSeverity(ImpactSeverity.HIGH, IssueSeverity.BLOCKER)).isNotNull();
    assertThat(SonarLintSeverity.fromCoreSeverity(ImpactSeverity.MEDIUM, IssueSeverity.MAJOR)).isNotNull();
    assertThat(SonarLintSeverity.fromCoreSeverity(ImpactSeverity.LOW, IssueSeverity.MINOR)).isNotNull();
    assertThat(SonarLintSeverity.fromCoreSeverity(ImpactSeverity.LOW, IssueSeverity.INFO)).isNotNull();
    assertThat(SonarLintSeverity.fromCoreSeverity(ImpactSeverity.HIGH, IssueSeverity.CRITICAL)).isNotNull();
  }

  @Test
  void testBlockerSeverity() {
    assertThat(SonarLintSeverity.HIGH.highlightSeverity()).isEqualTo(HighlightSeverity.WARNING);
    assertThat(SonarLintSeverity.HIGH.highlightType()).isEqualTo(ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    assertThat(SonarLintSeverity.HIGH.defaultTextAttributes()).isEqualTo(CodeInsightColors.WARNINGS_ATTRIBUTES);
  }

  @Test
  void testInfoSeverity() {
    assertThat(SonarLintSeverity.LOW.highlightSeverity()).isEqualTo(HighlightSeverity.WARNING);
    assertThat(SonarLintSeverity.LOW.highlightType()).isEqualTo(ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    assertThat(SonarLintSeverity.LOW.defaultTextAttributes()).isEqualTo(CodeInsightColors.WARNINGS_ATTRIBUTES);
  }
}
