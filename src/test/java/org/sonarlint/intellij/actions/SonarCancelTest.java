/**
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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
package org.sonarlint.intellij.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.analysis.SonarLintStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SonarCancelTest extends SonarTest {
  private SonarCancel sonarCancel;

  private AnActionEvent event;

  @Before
  public void setUp() {
    super.setUp();
    register(SonarLintStatus.class, new SonarLintStatus(project));
    sonarCancel = new SonarCancel();
    event = SonarLintTestUtils.createAnActionEvent(project);
  }

  @Test
  public void testCancel() {
    SonarLintStatus status = SonarLintStatus.get(project);

    status.stopRun();
    status.tryRun();
    assertThat(status.isRunning()).isTrue();
    assertThat(status.isCanceled()).isFalse();


    sonarCancel.actionPerformed(event);

    assertThat(status.isRunning()).isTrue();
    assertThat(status.isCanceled()).isTrue();
  }

  @Test
  public void testDisableIfNotRunning() {
    SonarLintStatus status = mock(SonarLintStatus.class);
    when(status.isRunning()).thenReturn(false);
    assertThat(sonarCancel.isEnabled(status)).isFalse();
  }

  @Test
  public void testEnableIfRunning() {
    SonarLintStatus status = mock(SonarLintStatus.class);
    when(status.isRunning()).thenReturn(true);
    assertThat(sonarCancel.isEnabled(status)).isTrue();
  }

}
