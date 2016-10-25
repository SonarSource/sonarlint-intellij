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

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonarlint.intellij.SonarTest;
import org.sonarlint.intellij.issue.IssueManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class SonarClearIssuesTest extends SonarTest {
  @Mock
  private FileEditorManager editorManager;
  @Mock
  private DaemonCodeAnalyzer codeAnalyzer;
  @Mock
  private IssueManager issueManager;
  @Mock
  private PsiManager psiManager;
  @Mock
  private AnActionEvent event;

  private SonarClearIssues clearIssues;

  @Before
  public void setUp() {
    super.setUp();
    MockitoAnnotations.initMocks(this);
    when(event.getProject()).thenReturn(project);
    when(app.acquireReadActionLock()).thenReturn(mock(AccessToken.class));

    super.register(IssueManager.class, issueManager);
    super.register(DaemonCodeAnalyzer.class, codeAnalyzer);
    super.register(FileEditorManager.class, editorManager);
    super.register(PsiManager.class, psiManager);

    clearIssues = new SonarClearIssues();
  }

  @Test
  public void testClear() {
    VirtualFile openFile = mock(VirtualFile.class);
    PsiFile psiFile = mock(PsiFile.class);

    when(editorManager.getOpenFiles()).thenReturn(new VirtualFile[] { openFile });
    when(openFile.isValid()).thenReturn(true);
    when(psiManager.findFile(openFile)).thenReturn(psiFile);

    clearIssues.actionPerformed(event);

    verify(codeAnalyzer).restart(psiFile);
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
