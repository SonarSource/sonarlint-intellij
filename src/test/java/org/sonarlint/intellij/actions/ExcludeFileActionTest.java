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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.config.project.SonarLintProjectSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExcludeFileActionTest extends SonarTest {
  private VirtualFile file1 = mock(VirtualFile.class);
  private SonarLintProjectSettings settings = new SonarLintProjectSettings();

  private ExcludeFileAction action = new ExcludeFileAction();

  @Before
  public void setup() {
    super.register(project, SonarLintProjectSettings.class, settings);
  }

  @Test
  public void add_exclusion() {
    AnActionEvent e = mock(AnActionEvent.class);
    when(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)).thenReturn(new VirtualFile[] {file1});
    when(e.getProject()).thenReturn(project);
    when(project.getBasePath()).thenReturn("/root");
    when(file1.getPath()).thenReturn("/root/file1");

    action.actionPerformed(e);

    assertThat(settings.getFileExclusions()).containsOnly("FILE:file1");
  }

  @Test
  public void dont_add_exclusion_if_already_exists() {
    AnActionEvent e = mock(AnActionEvent.class);
    when(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)).thenReturn(new VirtualFile[] {file1});
    when(e.getProject()).thenReturn(project);
    when(project.getBasePath()).thenReturn("/root");
    when(file1.getPath()).thenReturn("/root/file1");
    settings.setFileExclusions(Collections.singletonList("FILE:file1"));

    action.actionPerformed(e);

    assertThat(settings.getFileExclusions()).containsOnly("FILE:file1");
  }

  @Test
  public void do_nothing_if_disposed() {
    AnActionEvent e = mock(AnActionEvent.class);
    when(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)).thenReturn(new VirtualFile[] {file1});
    when(e.getProject()).thenReturn(project);
    when(project.isDisposed()).thenReturn(true);
    when(project.getBasePath()).thenReturn("/root");
    when(file1.getPath()).thenReturn("/root/file1");

    action.actionPerformed(e);

    assertThat(settings.getFileExclusions()).isEmpty();
  }

  @Test
  public void reject_project() {
    AnActionEvent e = mock(AnActionEvent.class);
    when(e.getProject()).thenReturn(project);
    when(project.getBasePath()).thenReturn("/root");
    when(file1.getPath()).thenReturn("/root");

    action.actionPerformed(e);
    assertThat(settings.getFileExclusions()).isEmpty();
  }

  @Test
  public void do_nothing_if_there_are_no_files() {
    AnActionEvent e = mock(AnActionEvent.class);
    when(e.getProject()).thenReturn(project);
    when(project.isDisposed()).thenReturn(true);
    when(project.getBasePath()).thenReturn("/root");
    when(file1.getPath()).thenReturn("/root/file1");

    action.actionPerformed(e);

    assertThat(settings.getFileExclusions()).isEmpty();
  }
}
