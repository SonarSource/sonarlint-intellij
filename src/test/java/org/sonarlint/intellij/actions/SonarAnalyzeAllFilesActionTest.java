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
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.analysis.AnalysisCallback;
import org.sonarlint.intellij.analysis.SonarLintStatus;
import org.sonarlint.intellij.issue.AnalysisResultIssues;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.trigger.SonarLintSubmitter;
import org.sonarlint.intellij.trigger.TriggerType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SonarAnalyzeAllFilesActionTest extends SonarTest {
  private SonarLintSubmitter submitter = register(SonarLintSubmitter.class);
  private SonarLintStatus status = mock(SonarLintStatus.class);
  private AnalysisResultIssues issues = register(AnalysisResultIssues.class);
  private IssueManager issueManager = register(IssueManager.class);
  private ProjectRootManager projectRootManager = register(ProjectRootManager.class);
  private ProjectFileIndex projectFileIndex = mock(ProjectFileIndex.class);
  private AnActionEvent event = mock(AnActionEvent.class);

  private SonarAnalyzeAllFilesAction action = new SonarAnalyzeAllFilesAction();

  @Before
  public void before() {
    when(projectRootManager.getFileIndex()).thenReturn(projectFileIndex);
  }

  @Test
  public void testEnabled() {
    VirtualFile file = mock(VirtualFile.class);
    mockProjectFiles(file);

    assertThat(action.isVisible("")).isTrue();
    when(status.isRunning()).thenReturn(true);
    assertThat(action.isEnabled(event, project, status)).isFalse();

    when(status.isRunning()).thenReturn(false);
    assertThat(action.isEnabled(event, project, status)).isTrue();
  }

  @Test
  public void testNoProject() {
    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(null);
    action.actionPerformed(event);
  }

  @Test
  public void testRun() {
    AnActionEvent event = mock(AnActionEvent.class);
    VirtualFile file = mock(VirtualFile.class);
    mockProjectFiles(file);
    when(event.getProject()).thenReturn(project);
    Messages.setTestDialog(x -> Messages.OK);
    action.actionPerformed(event);

    verify(submitter).submitFiles(eq(Collections.singletonList(file)), eq(TriggerType.ALL), any(AnalysisCallback.class), eq(false));
  }

  private void mockProjectFiles(VirtualFile file) {
    VirtualFile dir = mock(VirtualFile.class);
    when(file.isDirectory()).thenReturn(false);
    when(dir.isDirectory()).thenReturn(true);
    when(projectFileIndex.iterateContent(any(ContentIterator.class))).then(i -> {
      ((ContentIterator) i.getArgument(0)).processFile(file);
      ((ContentIterator) i.getArgument(0)).processFile(dir);
      return true;
    });
  }
}
