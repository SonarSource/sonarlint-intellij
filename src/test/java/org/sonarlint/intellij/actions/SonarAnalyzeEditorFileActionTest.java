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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.analysis.SonarLintJobManager;
import org.sonarlint.intellij.analysis.SonarLintStatus;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintAppUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class SonarAnalyzeEditorFileActionTest extends SonarTest {
  @Mock
  private SonarLintAppUtils utils;
  @Mock
  private SonarLintConsole console;
  @Mock
  private SonarLintJobManager jobManager;

  private SonarAnalyzeEditorFileAction editorFileAction;
  private AnActionEvent event;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    super.setUp();
    super.register(app, SonarLintAppUtils.class, utils);
    super.register(SonarLintJobManager.class, jobManager);
    super.register(SonarLintConsole.class, console);

    editorFileAction = new SonarAnalyzeEditorFileAction();
    event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(project);
  }

  @Test
  public void should_submit() {
    VirtualFile f1 = mock(VirtualFile.class);
    Module m1 = mock(Module.class);
    when(utils.getSelectedFile(project)).thenReturn(f1);
    when(utils.findModuleForFile(f1, project)).thenReturn(m1);
    when(utils.shouldAnalyze(f1, m1)).thenReturn(true);

    editorFileAction.actionPerformed(event);
    verify(jobManager).submit(m1, Collections.singleton(f1), TriggerType.ACTION);
  }

  @Test
  public void should_do_nothing_if_no_file() {
    when(utils.getSelectedFile(project)).thenReturn(null);

    editorFileAction.actionPerformed(event);
    verifyZeroInteractions(jobManager);
  }

  @Test
  public void should_do_nothing_if_no_project() {
    when(event.getProject()).thenReturn(null);
    editorFileAction.actionPerformed(event);
    verifyZeroInteractions(jobManager);
  }

  @Test
  public void should_be_enabled_if_file_and_not_running() {
    VirtualFile f1 = mock(VirtualFile.class);
    when(utils.getSelectedFile(project)).thenReturn(f1);

    SonarLintStatus status = new SonarLintStatus(project);
    status.tryRun();
    assertThat(editorFileAction.isEnabled(project, status)).isFalse();

    status.stopRun();
    assertThat(editorFileAction.isEnabled(project, status)).isTrue();

    when(utils.getSelectedFile(project)).thenReturn(null);
    assertThat(editorFileAction.isEnabled(project, status)).isFalse();
  }
}
