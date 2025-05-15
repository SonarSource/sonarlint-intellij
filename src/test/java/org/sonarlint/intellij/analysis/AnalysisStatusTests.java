/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisStatusTests extends AbstractSonarLintLightTests {
  private AnalysisStatus status;

  @BeforeEach
  void prepare() {
    status = new AnalysisStatus(getProject());
  }

  @Test
  void test_initial_status() {
    assertStatus(false, false);
  }

  @Test
  void test_run_once() {
    assertThat(status.tryRun()).isTrue();
    assertStatus(true, false);
  }

  @Test
  void test_run_twice() {
    assertThat(status.tryRun()).isTrue();
    assertThat(status.tryRun()).isFalse();
    assertStatus(true, false);
  }

  @Test
  void test_cancel_not_running() {
    status.cancel();
    assertStatus(false, false);
  }

  @Test
  void test_cancel_running() {
    status.tryRun();
    status.cancel();
    assertStatus(true, true);

    status.cancel();
    assertStatus(true, true);
  }

  @Test
  void test_stop_not_running() {
    status.stopRun();
    assertStatus(false, false);
  }

  @Test
  void test_stop_running() {
    status.tryRun();
    status.stopRun();
    assertStatus(false, false);
  }

  @Test
  void test_stop_cancel() {
    status.tryRun();
    status.cancel();
    status.stopRun();
    assertStatus(false, false);
  }

  private void assertStatus(boolean running, boolean canceled) {
    assertThat(status.isRunning()).isEqualTo(running);
    assertThat(status.isCanceled()).isEqualTo(canceled);
  }

}
