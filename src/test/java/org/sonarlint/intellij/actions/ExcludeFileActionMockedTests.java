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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintMockedTests;
import org.sonarlint.intellij.config.project.SonarLintProjectSettingsStore;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;

import static com.intellij.openapi.actionSystem.ActionPlaces.EDITOR_POPUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class ExcludeFileActionMockedTests extends AbstractSonarLintMockedTests {
  private VirtualFile file1 = mock(VirtualFile.class);
  private SonarLintProjectSettingsStore settings = new SonarLintProjectSettingsStore();
  private SonarLintSubmitter submitter = mock(SonarLintSubmitter.class);
  private ExcludeFileAction action = new ExcludeFileAction();
  private AnActionEvent e = mock(AnActionEvent.class);
  private Presentation presentation = new Presentation();

  @Before
  public void setup() {
    super.register(project, SonarLintProjectSettingsStore.class, settings);
    super.register(project, SonarLintSubmitter.class, submitter);
    when(e.getProject()).thenReturn(project);
    when(e.getPresentation()).thenReturn(presentation);
    when(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)).thenReturn(new VirtualFile[] {file1});
    when(e.getPlace()).thenReturn(EDITOR_POPUP);
  }

  @Test
  public void do_nothing_if_disposed() {
    project.setDisposed(true);

    action.actionPerformed(e);

    assertThat(settings.getState().getFileExclusions()).isEmpty();
    verifyZeroInteractions(submitter);
  }

  @Test
  public void do_nothing_if_there_are_no_files() {
    project.setDisposed(true);

    action.actionPerformed(e);

    assertThat(settings.getState().getFileExclusions()).isEmpty();
    verifyZeroInteractions(submitter);
  }

  @Test
  public void disable_if_project_is_not_init() {
    project.setInitialized(false);
    action.update(e);
    assertThat(presentation.isVisible()).isTrue();
    assertThat(presentation.isEnabled()).isFalse();
  }

  @Test
  public void disable_if_project_is_disposed() {
    project.setDisposed(true);
    action.update(e);
    assertThat(presentation.isVisible()).isTrue();
    assertThat(presentation.isEnabled()).isFalse();
  }

  @Test
  public void disable_if_project_is_null() {
    when(e.getProject()).thenReturn(null);
    action.update(e);
    assertThat(presentation.isVisible()).isTrue();
    assertThat(presentation.isEnabled()).isFalse();
  }

  @Test
  public void invisible_if_no_file() {
    when(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)).thenReturn(new VirtualFile[0]);
    action.update(e);
    assertThat(presentation.isVisible()).isFalse();
    assertThat(presentation.isEnabled()).isFalse();
  }

}
