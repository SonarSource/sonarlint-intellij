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
package org.sonarlint.intellij.config.global;

import java.util.Collections;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;

import static org.assertj.core.api.Assertions.assertThat;

public class SonarLintGlobalSettingsTest extends SonarTest {

  @Test
  public void testRoundTrip() {
    SonarLintGlobalSettings settings = new SonarLintGlobalSettings();
    SonarQubeServer server = SonarQubeServer.newBuilder().setName("name").build();

    settings.setSonarQubeServers(Collections.singletonList(server));
    assertThat(settings.getSonarQubeServers()).containsOnly(server);

    settings.setAutoTrigger(true);
    assertThat(settings.isAutoTrigger()).isTrue();

    assertThat(settings.getComponentName()).isEqualTo("SonarLintGlobalSettings");
    assertThat(settings.getPresentableName()).isEqualTo("SonarLint settings");

    assertThat(settings.getState()).isEqualTo(settings);
    assertThat(settings.getExportFiles()).isNotEmpty();
  }

  @Test
  public void testGetInstance() {
    SonarLintGlobalSettings instance = new SonarLintGlobalSettings();
    super.register(app, SonarLintGlobalSettings.class, instance);
    assertThat(SonarLintGlobalSettings.getInstance()).isEqualTo(instance);

  }

}
