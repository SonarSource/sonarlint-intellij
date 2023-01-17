/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
import org.junit.Test;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;

import static org.assertj.core.api.Assertions.assertThat;

public class SonarLintSeverityTest {
  @Test
  public void testSeveritiesExist() {
    assertThat(SonarLintSeverity.fromCoreSeverity(IssueSeverity.BLOCKER)).isNotNull();
    assertThat(SonarLintSeverity.fromCoreSeverity(IssueSeverity.MAJOR)).isNotNull();
    assertThat(SonarLintSeverity.fromCoreSeverity(IssueSeverity.MINOR)).isNotNull();
    assertThat(SonarLintSeverity.fromCoreSeverity(IssueSeverity.INFO)).isNotNull();
    assertThat(SonarLintSeverity.fromCoreSeverity(IssueSeverity.CRITICAL)).isNotNull();
  }

  @Test
  public void testBlockerSeverity() {
    assertThat(SonarLintSeverity.BLOCKER.highlightSeverity()).isEqualTo(HighlightSeverity.WARNING);
    assertThat(SonarLintSeverity.BLOCKER.highlightType()).isEqualTo(ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    assertThat(SonarLintSeverity.BLOCKER.defaultTextAttributes()).isEqualTo(CodeInsightColors.WARNINGS_ATTRIBUTES);
  }

  @Test
  public void testInfoSeverity() {
    assertThat(SonarLintSeverity.INFO.highlightSeverity()).isEqualTo(HighlightSeverity.WEAK_WARNING);
    assertThat(SonarLintSeverity.INFO.highlightType()).isEqualTo(ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    assertThat(SonarLintSeverity.INFO.defaultTextAttributes()).isEqualTo(CodeInsightColors.WARNINGS_ATTRIBUTES);
  }
}
