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
package org.sonarlint.intellij.config.global.wizard;

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;

import static org.assertj.core.api.Assertions.assertThat;

class ServerStepTests {

  @Test
  void selected_us_radio_wins() {
    assertThat(ServerStep.resolveRegion(true, false, SonarCloudRegion.EU)).isEqualTo(SonarCloudRegion.US);
    assertThat(ServerStep.resolveRegion(true, false, SonarCloudRegion.AU)).isEqualTo(SonarCloudRegion.US);
  }

  @Test
  void selected_eu_radio_wins() {
    assertThat(ServerStep.resolveRegion(false, true, SonarCloudRegion.US)).isEqualTo(SonarCloudRegion.EU);
    assertThat(ServerStep.resolveRegion(false, true, SonarCloudRegion.AU)).isEqualTo(SonarCloudRegion.EU);
  }

  @Test
  void existing_au_region_is_kept_when_no_radio_is_selected() {
    assertThat(ServerStep.resolveRegion(false, false, SonarCloudRegion.AU)).isEqualTo(SonarCloudRegion.AU);
  }
}
