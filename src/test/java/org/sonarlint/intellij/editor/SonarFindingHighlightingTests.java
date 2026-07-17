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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.config.Settings;
import org.sonarlint.intellij.config.SonarLintTextAttributes;
import org.sonarlint.intellij.config.global.ServerConnection;
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity;
import org.sonarsource.sonarlint.core.rpc.protocol.common.IssueSeverity;

import static org.assertj.core.api.Assertions.assertThat;

class SonarFindingHighlightingTests extends AbstractSonarLintLightTests {

  @BeforeEach
  void set() {
    Settings.getGlobalSettings().setFocusOnNewCode(false);
  }

  @Test
  void testSeverityMapping() {
    assertThat(SonarFindingHighlighting.getTextAttrsKey(null, null, false, false)).isEqualTo(SonarLintTextAttributes.MEDIUM);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(null, IssueSeverity.MAJOR, false, false)).isEqualTo(SonarLintTextAttributes.MEDIUM);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(null, IssueSeverity.MINOR, false, false)).isEqualTo(SonarLintTextAttributes.LOW);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(null, IssueSeverity.BLOCKER, false, false)).isEqualTo(SonarLintTextAttributes.BLOCKER);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(null, IssueSeverity.CRITICAL, false, false)).isEqualTo(SonarLintTextAttributes.HIGH);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(null, IssueSeverity.INFO, false, false)).isEqualTo(SonarLintTextAttributes.INFO);

    connectProjectTo(ServerConnection.newBuilder().setName("connection").build(), "projectKey");

    assertThat(SonarFindingHighlighting.getTextAttrsKey(null, IssueSeverity.MAJOR, false, true)).isEqualTo(SonarLintTextAttributes.OLD_CODE);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(null, IssueSeverity.MINOR, false, true)).isEqualTo(SonarLintTextAttributes.OLD_CODE);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(null, IssueSeverity.BLOCKER, false, true)).isEqualTo(SonarLintTextAttributes.OLD_CODE);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(null, IssueSeverity.CRITICAL, false, true)).isEqualTo(SonarLintTextAttributes.OLD_CODE);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(null, IssueSeverity.INFO, false, true)).isEqualTo(SonarLintTextAttributes.OLD_CODE);

    assertThat(SonarFindingHighlighting.getTextAttrsKey(null, IssueSeverity.MAJOR, true, true)).isEqualTo(SonarLintTextAttributes.MEDIUM);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(null, IssueSeverity.MINOR, true, true)).isEqualTo(SonarLintTextAttributes.LOW);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(null, IssueSeverity.BLOCKER, true, true)).isEqualTo(SonarLintTextAttributes.BLOCKER);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(null, IssueSeverity.CRITICAL, true, true)).isEqualTo(SonarLintTextAttributes.HIGH);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(null, IssueSeverity.INFO, true, true)).isEqualTo(SonarLintTextAttributes.INFO);
  }

  @Test
  void testImpactMapping() {
    assertThat(SonarFindingHighlighting.getTextAttrsKey(null, null, false, false)).isEqualTo(SonarLintTextAttributes.MEDIUM);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(ImpactSeverity.MEDIUM, IssueSeverity.MAJOR, false, false)).isEqualTo(SonarLintTextAttributes.MEDIUM);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(ImpactSeverity.LOW, IssueSeverity.MINOR, false, false)).isEqualTo(SonarLintTextAttributes.LOW);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(ImpactSeverity.HIGH, IssueSeverity.BLOCKER, false, false)).isEqualTo(SonarLintTextAttributes.HIGH);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(ImpactSeverity.HIGH, IssueSeverity.CRITICAL, false, false)).isEqualTo(SonarLintTextAttributes.HIGH);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(ImpactSeverity.LOW, IssueSeverity.INFO, false, false)).isEqualTo(SonarLintTextAttributes.LOW);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(ImpactSeverity.BLOCKER, IssueSeverity.MAJOR, false, false)).isEqualTo(SonarLintTextAttributes.BLOCKER);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(ImpactSeverity.INFO, IssueSeverity.MAJOR, false, false)).isEqualTo(SonarLintTextAttributes.INFO);

    connectProjectTo(ServerConnection.newBuilder().setName("connection").build(), "projectKey");

    assertThat(SonarFindingHighlighting.getTextAttrsKey(ImpactSeverity.MEDIUM, IssueSeverity.MAJOR, false, true)).isEqualTo(SonarLintTextAttributes.OLD_CODE);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(ImpactSeverity.LOW, IssueSeverity.MINOR, false, true)).isEqualTo(SonarLintTextAttributes.OLD_CODE);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(ImpactSeverity.HIGH, IssueSeverity.BLOCKER, false, true)).isEqualTo(SonarLintTextAttributes.OLD_CODE);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(ImpactSeverity.HIGH, IssueSeverity.CRITICAL, false, true)).isEqualTo(SonarLintTextAttributes.OLD_CODE);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(ImpactSeverity.LOW, IssueSeverity.INFO, false, true)).isEqualTo(SonarLintTextAttributes.OLD_CODE);

    assertThat(SonarFindingHighlighting.getTextAttrsKey(ImpactSeverity.MEDIUM, IssueSeverity.MAJOR, true, true)).isEqualTo(SonarLintTextAttributes.MEDIUM);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(ImpactSeverity.LOW, IssueSeverity.MINOR, true, true)).isEqualTo(SonarLintTextAttributes.LOW);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(ImpactSeverity.HIGH, IssueSeverity.BLOCKER, true, true)).isEqualTo(SonarLintTextAttributes.HIGH);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(ImpactSeverity.HIGH, IssueSeverity.CRITICAL, true, true)).isEqualTo(SonarLintTextAttributes.HIGH);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(ImpactSeverity.LOW, IssueSeverity.INFO, true, true)).isEqualTo(SonarLintTextAttributes.LOW);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(ImpactSeverity.BLOCKER, IssueSeverity.INFO, true, true)).isEqualTo(SonarLintTextAttributes.BLOCKER);
    assertThat(SonarFindingHighlighting.getTextAttrsKey(ImpactSeverity.INFO, IssueSeverity.INFO, true, true)).isEqualTo(SonarLintTextAttributes.INFO);
  }
}
