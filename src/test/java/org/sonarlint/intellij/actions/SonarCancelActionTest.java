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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import java.io.File;
import java.io.IOException;
import org.jdom.JDOMException;
import org.junit.Test;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.analysis.SonarLintStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Heavy because of the need to close the project
public class SonarCancelActionTest extends HeavyPlatformTestCase {
  private SonarCancelAction sonarCancelAction = new SonarCancelAction();
  private Presentation presentation = new Presentation();
  private AnActionEvent event;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    event = SonarLintTestUtils.createAnActionEvent(getProject());
    when(event.getPresentation()).thenReturn(presentation);
  }

  @Test
  public void testCancel() {
    SonarLintStatus status = SonarLintStatus.get(getProject());

    status.stopRun();
    status.tryRun();
    assertThat(status.isRunning()).isTrue();
    assertThat(status.isCanceled()).isFalse();

    sonarCancelAction.actionPerformed(event);

    assertThat(status.isRunning()).isTrue();
    assertThat(status.isCanceled()).isTrue();
  }

//  @Test
//  public void testUpdate() throws IOException, JDOMException {
//    sonarCancelAction.update(event);
//    assertThat(presentation.isVisible()).isTrue();
//    assertThat(presentation.isEnabled()).isFalse();
//
//    SonarLintStatus status = SonarLintStatus.get(getProject());
//    status.tryRun();
//    sonarCancelAction.update(event);
//    assertThat(presentation.isEnabled()).isTrue();
//
//    File projectDir = FileUtil.createTempDirectory("project", null);
//    Project project = ProjectManager.getInstance().createProject("project", projectDir.getAbsolutePath());
//    disposeOnTearDown(project);
//    ProjectManager projectManager = ProjectManager.getInstance();
//    Project reloaded = projectManager.loadAndOpenProject(projectDir);
//    disposeOnTearDown(reloaded);
//    event = SonarLintTestUtils.createAnActionEvent(reloaded);
//    when(event.getPresentation()).thenReturn(presentation);
//    ((ProjectManagerImpl) ProjectManager.getInstance()).forceCloseProject(reloaded);
//    sonarCancelAction.update(event);
//    assertThat(presentation.isEnabled()).isFalse();
//  }

  @Test
  public void testCancelWithoutProject() {
    event = SonarLintTestUtils.createAnActionEvent(null);
    sonarCancelAction.actionPerformed(event);

    assertThat(SonarLintStatus.get(getProject()).isRunning()).isFalse();
    assertThat(SonarLintStatus.get(getProject()).isCanceled()).isFalse();
  }

  @Test
  public void testDisableIfNotRunning() {
    SonarLintStatus status = mock(SonarLintStatus.class);
    when(status.isRunning()).thenReturn(false);
    assertThat(sonarCancelAction.isEnabled(event, getProject(), status)).isFalse();
  }

//  @Test
//  public void testDisableIfCanceled() {
//    SonarLintStatus status = mock(SonarLintStatus.class);
//    when(status.isRunning()).thenReturn(true);
//    when(status.isCanceled()).thenReturn(true);
//    assertThat(sonarCancelAction.isEnabled(event, getProject(), status)).isFalse();
//  }

  @Test
  public void testEnableIfRunning() {
    SonarLintStatus status = mock(SonarLintStatus.class);
    when(status.isRunning()).thenReturn(true);
    assertThat(sonarCancelAction.isEnabled(event, getProject(), status)).isTrue();
  }

}
