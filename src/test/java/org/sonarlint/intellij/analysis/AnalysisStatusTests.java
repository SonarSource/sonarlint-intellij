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
package org.sonarlint.intellij.analysis;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisStatusTests extends AbstractSonarLintLightTests {
  private static final UUID RANDOM_UUID = UUID.randomUUID();
  private AnalysisStatus status;

  @BeforeEach
  void prepare() {
    status = new AnalysisStatus(getProject());
  }

  @Test
  void test_initial_status() {
    assertThat(status.isRunning()).isFalse();
  }

  @Test
  void test_run_once() {
    assertThat(status.tryRun(RANDOM_UUID)).isTrue();
    assertThat(status.isRunning()).isTrue();
  }

  @Test
  void test_run_twice() {
    assertThat(status.tryRun(RANDOM_UUID)).isTrue();
    assertThat(status.tryRun(RANDOM_UUID)).isFalse();
    assertThat(status.isRunning()).isTrue();
  }

  @Test
  void test_stop_not_running() {
    status.stopRun(RANDOM_UUID);
    assertThat(status.isRunning()).isFalse();
  }

  @Test
  void test_stop_running() {
    status.tryRun(RANDOM_UUID);
    status.stopRun(RANDOM_UUID);
    assertThat(status.isRunning()).isFalse();
  }

}
