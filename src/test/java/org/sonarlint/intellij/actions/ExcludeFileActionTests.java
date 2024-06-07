/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintHeavyTests;
import org.sonarlint.intellij.analysis.AnalysisReadinessCache;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;
import org.sonarlint.intellij.analysis.RunningAnalysesTracker;
import org.sonarlint.intellij.config.project.SonarLintProjectSettingsStore;

import static com.intellij.openapi.actionSystem.ActionPlaces.EDITOR_POPUP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

class ExcludeFileActionTests extends AbstractSonarLintHeavyTests {
  private final VirtualFile file1 = mock(VirtualFile.class);
  private final SonarLintProjectSettingsStore settings = new SonarLintProjectSettingsStore();
  private final AnalysisSubmitter analysisSubmitter = mock(AnalysisSubmitter.class);
  private final ExcludeFileAction action = new ExcludeFileAction();
  private final AnActionEvent e = mock(AnActionEvent.class);
  private final Presentation presentation = new Presentation();
  private Project projectSpy;

  @BeforeEach
  void setup() {
    replaceProjectService(SonarLintProjectSettingsStore.class, settings);
    replaceProjectService(AnalysisSubmitter.class, analysisSubmitter);
    projectSpy = spy(getProject());
    when(projectSpy.isInitialized()).thenReturn(true);
    when(e.getProject()).thenReturn(projectSpy);
    when(e.getPresentation()).thenReturn(presentation);
    when(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)).thenReturn(new VirtualFile[] {file1});
    when(e.getPlace()).thenReturn(EDITOR_POPUP);

    Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(getService(myProject, AnalysisReadinessCache.class).isReady()).isTrue());
    Awaitility.await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> assertThat(getService(myProject, RunningAnalysesTracker.class).isAnalysisRunning()).isFalse());
    clearInvocations(analysisSubmitter);
  }

  @Test
  void do_nothing_if_disposed() {
    when(projectSpy.isDisposed()).thenReturn(true);

    action.actionPerformed(e);

    assertThat(settings.getState().getFileExclusions()).isEmpty();
    verifyNoMoreInteractions(analysisSubmitter);
  }

  @Test
  void do_nothing_if_there_are_no_files() {
    when(projectSpy.isDisposed()).thenReturn(true);

    action.actionPerformed(e);
    
    assertThat(settings.getState().getFileExclusions()).isEmpty();
    verifyNoMoreInteractions(analysisSubmitter);
  }

  @Test
  void disable_if_project_is_not_init() {
    when(projectSpy.isInitialized()).thenReturn(false);

    action.update(e);

    assertThat(presentation.isVisible()).isFalse();
    assertThat(presentation.isEnabled()).isFalse();
  }

  @Test
  void disable_if_project_is_disposed() {
    when(projectSpy.isDisposed()).thenReturn(true);

    action.update(e);

    assertThat(presentation.isVisible()).isFalse();
    assertThat(presentation.isEnabled()).isFalse();
  }

  @Test
  void disable_if_project_is_null() {
    when(e.getProject()).thenReturn(null);

    action.update(e);

    assertThat(presentation.isVisible()).isFalse();
    assertThat(presentation.isEnabled()).isFalse();
  }

  @Test
  void invisible_if_no_file() {
    when(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)).thenReturn(new VirtualFile[0]);

    action.update(e);

    assertThat(presentation.isVisible()).isFalse();
    assertThat(presentation.isEnabled()).isFalse();
  }

}
