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
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.analysis.SonarLintStatus;
import org.sonarlint.intellij.issue.AnalysisResultIssues;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintAppUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class SonarAnalyzeFilesActionTest extends SonarTest {
  private SonarLintAppUtils utils = register(app, SonarLintAppUtils.class);
  private SonarLintConsole console = register(SonarLintConsole.class);
  private SonarLintSubmitter submitter = register(SonarLintSubmitter.class);
  private AnActionEvent event = mock(AnActionEvent.class);
  private AnalysisResultIssues analysisResults = register(AnalysisResultIssues.class);
  private IssueManager issueManager = register(IssueManager.class);

  private SonarLintStatus status;
  private Presentation presentation = new Presentation();
  private SonarAnalyzeFilesAction editorFileAction = new SonarAnalyzeFilesAction();

  @Before
  public void prepare() {
    status = new SonarLintStatus(project);
    super.register(project, SonarLintStatus.class, status);

    when(event.getProject()).thenReturn(project);
    when(project.isInitialized()).thenReturn(true);
    when(event.getPresentation()).thenReturn(presentation);
  }

  @Test
  public void should_submit() {
    VirtualFile f1 = mock(VirtualFile.class);
    when(f1.getPath()).thenReturn("file");
    mockSelectedFiles(f1);
    editorFileAction.actionPerformed(event);
    verify(submitter).submitFiles(anyCollection(), eq(TriggerType.ACTION), any(AnalysisCallback.class), eq(false));
  }

  private void mockSelectedFiles(VirtualFile file) {
    when(event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)).thenReturn(new VirtualFile[] {file});
  }

  @Test
  public void should_do_nothing_if_no_file() {
    editorFileAction.actionPerformed(event);
    verifyZeroInteractions(submitter);
  }

  @Test
  public void should_do_nothing_if_no_project() {
    when(event.getProject()).thenReturn(null);
    editorFileAction.actionPerformed(event);
    verifyZeroInteractions(submitter);
  }

  @Test
  public void should_be_enabled_if_file_and_not_running() {
    VirtualFile f1 = mock(VirtualFile.class);
    mockSelectedFiles(f1);

    status.tryRun();

    editorFileAction.update(event);
    assertThat(presentation.isEnabled()).isFalse();

    status.stopRun();
    editorFileAction.update(event);
    assertThat(presentation.isEnabled()).isTrue();
  }
}
