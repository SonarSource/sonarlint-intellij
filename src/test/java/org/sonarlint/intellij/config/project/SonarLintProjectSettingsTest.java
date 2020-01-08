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
package org.sonarlint.intellij.config.project;

import java.util.Collections;
import org.assertj.core.data.MapEntry;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SonarLintProjectSettingsTest {
  @Test
  public void testRoundTrip() {
    SonarLintProjectSettings settings = new SonarLintProjectSettings();

    settings.setBindingEnabled(true);
    assertThat(settings.isBindingEnabled()).isTrue();

    settings.setProjectKey("project1");
    assertThat(settings.getProjectKey()).isEqualTo("project1");

    settings.setServerId("server1");
    assertThat(settings.getServerId()).isEqualTo("server1");

    settings.setAnalysisLogsEnabled(true);
    assertThat(settings.isAnalysisLogsEnabled()).isTrue();

    settings.setVerboseEnabled(true);
    assertThat(settings.isVerboseEnabled()).isTrue();

    settings.setBindingEnabled(true);
    assertThat(settings.isBindingEnabled()).isTrue();

    assertThat(settings.getState()).isEqualTo(settings);

    settings.setAdditionalProperties(Collections.singletonMap("key", "value"));
    assertThat(settings.getAdditionalProperties()).containsExactly(MapEntry.entry("key", "value"));

    assertThat(settings.getComponentName()).isEqualTo("SonarLintProjectSettings");
  }
}
