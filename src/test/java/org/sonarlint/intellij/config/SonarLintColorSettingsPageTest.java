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

import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SonarLintColorSettingsPageTest {
  private final static String[] SEVERITIES = {"INFO", "MINOR", "MAJOR", "CRITICAL", "BLOCKER"};
  private SonarLintColorSettingsPage colorSettingsPage;

  @Before
  public void setUp() {
    colorSettingsPage = new SonarLintColorSettingsPage();
  }

  @Test
  public void testGetters() {
    assertThat(colorSettingsPage).isNotNull();
    assertThat(colorSettingsPage.getColorDescriptors()).isEmpty();
    assertThat(colorSettingsPage.getDisplayName()).isEqualTo("SonarLint");
    assertThat(colorSettingsPage.getHighlighter()).isInstanceOf(PlainSyntaxHighlighter.class);
  }

  @Test
  public void testDescriptors() {
    assertThat(colorSettingsPage.getAdditionalHighlightingTagToDescriptorMap()).containsValues(
      SonarLintTextAttributes.BLOCKER,
      SonarLintTextAttributes.INFO,
      SonarLintTextAttributes.MAJOR,
      SonarLintTextAttributes.MINOR,
      SonarLintTextAttributes.CRITICAL);
  }

  @Test
  public void testAttributeDescriptors() {
    // one per severity + selected
    assertThat(colorSettingsPage.getAttributeDescriptors()).hasSize(6);
  }

  @Test
  public void testIcon() {
    assertThat(colorSettingsPage.getIcon()).isNotNull();
  }

  @Test
  public void testDemo() {
    for (String txt : SEVERITIES) {
      assertThat(colorSettingsPage.getDemoText()).containsIgnoringCase(txt);
    }
  }
}
