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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.issue.IssueManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class SonarClearIssuesActionTest extends SonarTest {
  private FileEditorManager editorManager = register(FileEditorManager.class);
  private DaemonCodeAnalyzer codeAnalyzer = register(DaemonCodeAnalyzer.class);
  private IssueManager issueManager = register(IssueManager.class);
  private PsiManager psiManager = register(PsiManager.class);
  private AnActionEvent event = mock(AnActionEvent.class);

  private SonarClearIssuesAction clearIssues = new SonarClearIssuesAction(null, null, null);

  @Before
  public void prepare() {
    when(event.getProject()).thenReturn(project);
  }

  @Test
  public void testClear() {
    VirtualFile openFile = mock(VirtualFile.class);
    PsiFile psiFile = mock(PsiFile.class);

    when(editorManager.getOpenFiles()).thenReturn(new VirtualFile[] {openFile});
    when(openFile.isValid()).thenReturn(true);
    when(psiManager.findFile(openFile)).thenReturn(psiFile);

    clearIssues.actionPerformed(event);

    verify(codeAnalyzer).restart(psiFile);
    verify(issueManager).clear();
  }

  @Test
  public void testClearWithInvalidFiles() {
    VirtualFile openFile = mock(VirtualFile.class);

    when(editorManager.getOpenFiles()).thenReturn(new VirtualFile[] {openFile});
    when(openFile.isValid()).thenReturn(false);

    clearIssues.actionPerformed(event);

    verifyZeroInteractions(psiManager);
    verifyZeroInteractions(codeAnalyzer);
    verify(issueManager).clear();
  }

  @Test
  public void testDoNothingIfNoProject() {
    when(event.getProject()).thenReturn(null);

    clearIssues.actionPerformed(event);

    verifyZeroInteractions(codeAnalyzer);
    verifyZeroInteractions(issueManager);
  }
}
