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
package icons;

import org.junit.Test;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;
import org.sonarsource.sonarlint.core.commons.RuleType;
import org.sonarsource.sonarlint.core.commons.VulnerabilityProbability;

import static org.assertj.core.api.Assertions.assertThat;

public class SonarLintIconsTest {
  @Test
  public void testSeverities() {
    for (IssueSeverity value : IssueSeverity.values()) {
      assertThat(SonarLintIcons.severity(value)).isNotNull();
    }
  }

  @Test
  public void testTypes() {
    for (RuleType value : RuleType.values()) {
      assertThat(SonarLintIcons.type(value)).isNotNull();
    }
    for (VulnerabilityProbability value : VulnerabilityProbability.values()) {
      assertThat(SonarLintIcons.hotspotTypeWithProbability(value)).isNotNull();
    }
  }

  @Test
  public void testIcons() {
    assertThat(SonarLintIcons.CLEAN).isNotNull();
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
