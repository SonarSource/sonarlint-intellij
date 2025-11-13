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
package org.sonarlint.intellij.config;

import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SonarLintColorSettingsPageTests {
  private static final String[] SEVERITIES = {"LOW", "MEDIUM", "HIGH", "OLD", "SELECTED"};
  private SonarLintColorSettingsPage colorSettingsPage;

  @BeforeEach
  void setUp() {
    colorSettingsPage = new SonarLintColorSettingsPage();
  }

  @Test
  void testGetters() {
    assertThat(colorSettingsPage).isNotNull();
    assertThat(colorSettingsPage.getColorDescriptors()).isEmpty();
    assertThat(colorSettingsPage.getDisplayName()).isEqualTo("SonarQube for IDE");
    assertThat(colorSettingsPage.getHighlighter()).isInstanceOf(PlainSyntaxHighlighter.class);
  }

  @Test
  void testDescriptors() {
    assertThat(colorSettingsPage.getAdditionalHighlightingTagToDescriptorMap()).containsValues(
      SonarLintTextAttributes.BLOCKER,
      SonarLintTextAttributes.HIGH,
      SonarLintTextAttributes.MEDIUM,
      SonarLintTextAttributes.LOW,
      SonarLintTextAttributes.INFO);
  }

  @Test
  void testAttributeDescriptors() {
    // one per severity + selected
    assertThat(colorSettingsPage.getAttributeDescriptors()).hasSize(7);
  }

  @Test
  void testIcon() {
    assertThat(colorSettingsPage.getIcon()).isNotNull();
  }

  @Test
  void testDemo() {
    for (var txt : SEVERITIES) {
      assertThat(colorSettingsPage.getDemoText()).containsIgnoringCase(txt);
    }
  }
}
