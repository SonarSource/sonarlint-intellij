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
package org.sonarlint.intellij.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.analysis.SonarLintStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SonarCancelActionTest extends SonarTest {
  private SonarCancelAction sonarCancelAction = new SonarCancelAction();
  private Presentation presentation = new Presentation();
  private AnActionEvent event = SonarLintTestUtils.createAnActionEvent(project);

  @Before
  public void prepare() {
    register(SonarLintStatus.class, new SonarLintStatus(project));
    when(event.getPresentation()).thenReturn(presentation);
    when(project.isDisposed()).thenReturn(false);
    when(project.isInitialized()).thenReturn(true);
  }

  @Test
  public void testCancel() {
    SonarLintStatus status = SonarLintStatus.get(project);

    status.stopRun();
    status.tryRun();
    assertThat(status.isRunning()).isTrue();
    assertThat(status.isCanceled()).isFalse();

    sonarCancelAction.actionPerformed(event);

    assertThat(status.isRunning()).isTrue();
    assertThat(status.isCanceled()).isTrue();
  }

  @Test
  public void testUpdate() {
    sonarCancelAction.update(event);
    assertThat(presentation.isVisible()).isTrue();
    assertThat(presentation.isEnabled()).isFalse();

    SonarLintStatus status = SonarLintStatus.get(project);
    status.tryRun();
    sonarCancelAction.update(event);
    assertThat(presentation.isEnabled()).isTrue();

    when(project.isInitialized()).thenReturn(false);
    sonarCancelAction.update(event);
    assertThat(presentation.isEnabled()).isFalse();
  }

  @Test
  public void testCancelWithoutProject() {
    event = SonarLintTestUtils.createAnActionEvent(null);
    sonarCancelAction.actionPerformed(event);

    assertThat(SonarLintStatus.get(project).isRunning()).isFalse();
    assertThat(SonarLintStatus.get(project).isCanceled()).isFalse();
  }

  @Test
  public void testDisableIfNotRunning() {
    SonarLintStatus status = mock(SonarLintStatus.class);
    when(status.isRunning()).thenReturn(false);
    assertThat(sonarCancelAction.isEnabled(event, project, status)).isFalse();
  }

  @Test
  public void testDisableIfCanceled() {
    SonarLintStatus status = mock(SonarLintStatus.class);
    when(status.isRunning()).thenReturn(true);
    when(status.isCanceled()).thenReturn(true);
    assertThat(sonarCancelAction.isEnabled(event, project, status)).isFalse();
  }

  @Test
  public void testEnableIfRunning() {
    SonarLintStatus status = mock(SonarLintStatus.class);
    when(status.isRunning()).thenReturn(true);
    assertThat(sonarCancelAction.isEnabled(event, project, status)).isTrue();
  }

}
