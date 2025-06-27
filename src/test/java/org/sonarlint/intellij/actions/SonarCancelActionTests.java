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
package org.sonarlint.intellij.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.util.io.FileUtil;
import java.io.IOException;
import java.util.UUID;
import org.jdom.JDOMException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintHeavyTests;
import org.sonarlint.intellij.SonarLintTestUtils;
import org.sonarlint.intellij.analysis.AnalysisStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Heavy because of the need to close the project
class SonarCancelActionTests extends AbstractSonarLintHeavyTests {
  private static final UUID RANDOM_UUID = UUID.randomUUID();
  private final SonarCancelAction sonarCancelAction = new SonarCancelAction();
  private final Presentation presentation = new Presentation();
  private AnActionEvent event;

  @BeforeEach
  void prepare() {
    event = SonarLintTestUtils.createAnActionEvent(getProject());
    when(event.getProject()).thenReturn(myProject);
    when(event.getPresentation()).thenReturn(presentation);
  }

  @Test
  void testCancel() {
    var status = AnalysisStatus.get(getProject());

    status.stopRun(RANDOM_UUID);
    status.tryRun(RANDOM_UUID);

    assertThat(status.isRunning()).isTrue();

    sonarCancelAction.actionPerformed(event);

    assertThat(status.isRunning()).isFalse();
  }

  @Test
  void testUpdate() throws IOException, JDOMException {
    sonarCancelAction.update(event);
    assertThat(presentation.isVisible()).isTrue();
    assertThat(presentation.isEnabled()).isFalse();

    var status = AnalysisStatus.get(getProject());
    status.tryRun(RANDOM_UUID);
    sonarCancelAction.update(event);
    assertThat(presentation.isEnabled()).isTrue();

    var projectDir = FileUtil.createTempDirectory("project", null);
    var project = ProjectManager.getInstance().createProject("project", projectDir.getAbsolutePath());
    disposeOnTearDown(project);
    var projectManager = ProjectManager.getInstance();
    var reloaded = projectManager.loadAndOpenProject(projectDir.getAbsolutePath());
    disposeOnTearDown(reloaded);
    event = SonarLintTestUtils.createAnActionEvent(reloaded);
    when(event.getPresentation()).thenReturn(presentation);
    ((ProjectManagerImpl) ProjectManager.getInstance()).forceCloseProject(reloaded);
    sonarCancelAction.update(event);
    assertThat(presentation.isEnabled()).isFalse();
  }

  @Test
  void testCancelWithoutProject() {
    event = SonarLintTestUtils.createAnActionEvent(null);
    sonarCancelAction.actionPerformed(event);

    assertThat(AnalysisStatus.get(getProject()).isRunning()).isFalse();
  }

  @Test
  void testDisableIfNotRunning() {
    var status = mock(AnalysisStatus.class);
    when(status.isRunning()).thenReturn(false);
    assertThat(sonarCancelAction.isEnabled(event, getProject(), status)).isFalse();
  }

  @Test
  void testEnableIfRunning() {
    var status = mock(AnalysisStatus.class);
    when(status.isRunning()).thenReturn(true);
    assertThat(sonarCancelAction.isEnabled(event, getProject(), status)).isTrue();
  }

}
