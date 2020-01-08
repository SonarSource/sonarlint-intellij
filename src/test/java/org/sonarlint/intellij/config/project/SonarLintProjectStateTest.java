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

import java.time.ZonedDateTime;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SonarLintProjectStateTest {
  @Test
  public void testEmpty() {
    SonarLintProjectState state = new SonarLintProjectState();
    assertThat(state.getLastEventPolling()).isNull();
  }

  @Test
  public void testSet() {
    SonarLintProjectState state = new SonarLintProjectState();
    state.setLastEventPolling(ZonedDateTime.now());
    assertThat(state.getLastEventPolling()).isBeforeOrEqualTo(ZonedDateTime.now());
    assertThat(state.getLastEventPolling()).isAfter(ZonedDateTime.now().minusSeconds(3));
  }

  @Test
  public void testSerialization() {
    SonarLintProjectState state = new SonarLintProjectState();
    state.setLastEventPolling(ZonedDateTime.now().minusHours(2));

    SonarLintProjectState copy = state.getState();
    assertThat(copy.getLastEventPolling()).isEqualTo(state.getLastEventPolling());

    SonarLintProjectState loaded = new SonarLintProjectState();
    loaded.loadState(state);
    assertThat(loaded.getLastEventPolling()).isEqualTo(state.getLastEventPolling());
  }

}
