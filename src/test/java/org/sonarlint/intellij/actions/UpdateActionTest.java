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
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.analysis.SonarLintStatus;
import org.sonarlint.intellij.analysis.SonarQubeRunnerFacade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class UpdateActionTest extends LightPlatformCodeInsightFixtureTestCase {
  private UpdateAction action;
  private SonarQubeRunnerFacade runner;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    runner = SonarLintTestUtils.mockRunner(getProject());
    action = new UpdateAction();
    getProject().getComponent(SonarLintStatus.class).stopRun();
  }

  @Test
  public void testEnabled() {
    SonarLintStatus status = mock(SonarLintStatus.class);

    when(status.isRunning()).thenReturn(false);
    assertThat(action.isEnabled(status)).isTrue();

    when(status.isRunning()).thenReturn(true);
    assertThat(action.isEnabled(status)).isFalse();
  }

  @Test
  public void testAction() {
    action.actionPerformed(SonarLintTestUtils.createAnActionEvent(getProject()));

    verify(runner).tryUpdate();
    verify(runner).getVersion();
    verifyNoMoreInteractions(runner);

    SonarLintStatus status = getProject().getComponent(SonarLintStatus.class);
    assertThat(status.isRunning()).isFalse();
  }

  @Test
  public void testFailIfRunning() {
    SonarLintStatus status = getProject().getComponent(SonarLintStatus.class);
    status.tryRun();
    try {
      action.actionPerformed(SonarLintTestUtils.createAnActionEvent(getProject()));
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).isEqualTo("Unable to update SonarLint as an analysis is on-going");
    } finally {
      status.stopRun();
    }
  }
}
