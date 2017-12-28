/*
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
import com.intellij.openapi.actionSystem.Presentation;
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
  private Presentation presentation;
  private AnActionEvent event;

  @Before
  public void prepare() {
    register(SonarLintStatus.class, new SonarLintStatus(project));
    presentation = new Presentation();
    sonarCancel = new SonarCancel();
    event = SonarLintTestUtils.createAnActionEvent(project);
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

    sonarCancel.actionPerformed(event);

    assertThat(status.isRunning()).isTrue();
    assertThat(status.isCanceled()).isTrue();
  }

  @Test
  public void testUpdate() {
    sonarCancel.update(event);
    assertThat(presentation.isVisible()).isTrue();
    assertThat(presentation.isEnabled()).isFalse();

    SonarLintStatus status = SonarLintStatus.get(project);
    status.tryRun();
    sonarCancel.update(event);
    assertThat(presentation.isEnabled()).isTrue();

    when(project.isInitialized()).thenReturn(false);
    sonarCancel.update(event);
    assertThat(presentation.isEnabled()).isFalse();
  }

  @Test
  public void testCancelWithoutProject() {
    event = SonarLintTestUtils.createAnActionEvent(null);
    sonarCancel.actionPerformed(event);

    assertThat(SonarLintStatus.get(project).isRunning()).isFalse();
    assertThat(SonarLintStatus.get(project).isCanceled()).isFalse();
  }

  @Test
  public void testDisableIfNotRunning() {
    SonarLintStatus status = mock(SonarLintStatus.class);
    when(status.isRunning()).thenReturn(false);
    assertThat(sonarCancel.isEnabled(event, project, status)).isFalse();
  }

  @Test
  public void testDisableIfCanceled() {
    SonarLintStatus status = mock(SonarLintStatus.class);
    when(status.isRunning()).thenReturn(true);
    when(status.isCanceled()).thenReturn(true);
    assertThat(sonarCancel.isEnabled(event, project, status)).isFalse();
  }

  @Test
  public void testEnableIfRunning() {
    SonarLintStatus status = mock(SonarLintStatus.class);
    when(status.isRunning()).thenReturn(true);
    assertThat(sonarCancel.isEnabled(event, project, status)).isTrue();
  }

}
