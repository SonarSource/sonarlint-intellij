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
package icons;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SonarLintIconsTest {
  @Test
  public void testSeverities() {
    assertThat(SonarLintIcons.severity("MAJOR")).isNotNull();
    assertThat(SonarLintIcons.severity("MINOR")).isNotNull();
    assertThat(SonarLintIcons.severity("BLOCKER")).isNotNull();
    assertThat(SonarLintIcons.severity("INFO")).isNotNull();
    assertThat(SonarLintIcons.severity("CRITICAL")).isNotNull();

    assertThat(SonarLintIcons.severity12("MAJOR")).isNotNull();
    assertThat(SonarLintIcons.severity12("MINOR")).isNotNull();
    assertThat(SonarLintIcons.severity12("BLOCKER")).isNotNull();
    assertThat(SonarLintIcons.severity12("INFO")).isNotNull();
    assertThat(SonarLintIcons.severity12("CRITICAL")).isNotNull();
  }

  @Test
  public void testTypes() {
    assertThat(SonarLintIcons.type("BUG")).isNotNull();
    assertThat(SonarLintIcons.type("VULNERABILITY")).isNotNull();
    assertThat(SonarLintIcons.type("CODE_SMELL")).isNotNull();

    assertThat(SonarLintIcons.type12("BUG")).isNotNull();
    assertThat(SonarLintIcons.type12("VULNERABILITY")).isNotNull();
    assertThat(SonarLintIcons.type12("CODE_SMELL")).isNotNull();
  }

  @Test
  public void testIcons() {
    assertThat(SonarLintIcons.CLEAN).isNotNull();
    assertThat(SonarLintIcons.ICON_SONARLINT_13).isNotNull();
    assertThat(SonarLintIcons.ICON_SONARQUBE_16).isNotNull();
    assertThat(SonarLintIcons.INFO).isNotNull();
    assertThat(SonarLintIcons.PLAY).isNotNull();
    assertThat(SonarLintIcons.SONARLINT).isNotNull();
    assertThat(SonarLintIcons.SUSPEND).isNotNull();
    assertThat(SonarLintIcons.TOOLS).isNotNull();
    assertThat(SonarLintIcons.WARN).isNotNull();
  }

  @Test
  public void testDisabled() {
    assertThat(SonarLintIcons.toDisabled(SonarLintIcons.WARN)).isNotNull();
  }
}
