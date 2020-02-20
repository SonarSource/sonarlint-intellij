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
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.util.SonarLintAppUtils;

import static com.intellij.openapi.actionSystem.ActionPlaces.EDITOR_POPUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class ExcludeFileActionTest extends SonarTest {
  private VirtualFile file1 = mock(VirtualFile.class);
  private SonarLintProjectSettings settings = new SonarLintProjectSettings();
  private SonarLintAppUtils appUtils = mock(SonarLintAppUtils.class);
  private SonarLintSubmitter submitter = mock(SonarLintSubmitter.class);
  private ExcludeFileAction action = new ExcludeFileAction();
  private AnActionEvent e = mock(AnActionEvent.class);
  private Presentation presentation = new Presentation();

  @Before
  public void setup() {
    super.register(project, SonarLintProjectSettings.class, settings);
    super.register(app, SonarLintAppUtils.class, appUtils);
    super.register(project, SonarLintSubmitter.class, submitter);
    when(appUtils.getRelativePathForAnalysis(project, file1)).thenReturn("file1");
    when(project.isInitialized()).thenReturn(true);
    when(e.getProject()).thenReturn(project);
    when(e.getPresentation()).thenReturn(presentation);
    when(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)).thenReturn(new VirtualFile[] {file1});
    when(e.getPlace()).thenReturn(EDITOR_POPUP);
  }

  @Test
  public void add_exclusion() {
    action.actionPerformed(e);

    assertThat(settings.getFileExclusions()).containsOnly("FILE:file1");
    verify(submitter).submitOpenFilesAuto(TriggerType.CONFIG_CHANGE);
  }

  @Test
  public void dont_add_exclusion_if_already_exists() {
    settings.setFileExclusions(Collections.singletonList("FILE:file1"));

    action.actionPerformed(e);

    assertThat(settings.getFileExclusions()).containsOnly("FILE:file1");
    verifyZeroInteractions(submitter);
  }

  @Test
  public void do_nothing_if_disposed() {
    when(project.isDisposed()).thenReturn(true);

    action.actionPerformed(e);

    assertThat(settings.getFileExclusions()).isEmpty();
    verifyZeroInteractions(submitter);
  }

  @Test
  public void reject_project() {
    when(appUtils.getRelativePathForAnalysis(project, file1)).thenReturn("");

    action.actionPerformed(e);
    assertThat(settings.getFileExclusions()).isEmpty();
    verifyZeroInteractions(submitter);
  }

  @Test
  public void do_nothing_if_there_are_no_files() {
    when(project.isDisposed()).thenReturn(true);

    action.actionPerformed(e);

    assertThat(settings.getFileExclusions()).isEmpty();
    verifyZeroInteractions(submitter);
  }

  @Test
  public void disable_if_project_is_not_init() {
    when(project.isInitialized()).thenReturn(false);
    action.update(e);
    assertThat(presentation.isVisible()).isTrue();
    assertThat(presentation.isEnabled()).isFalse();
  }

  @Test
  public void disable_if_project_is_disposed() {
    when(project.isDisposed()).thenReturn(true);
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

  @Test
  public void enable_action() {
    action.update(e);
    assertThat(presentation.isVisible()).isTrue();
    assertThat(presentation.isEnabled()).isTrue();
  }
}
