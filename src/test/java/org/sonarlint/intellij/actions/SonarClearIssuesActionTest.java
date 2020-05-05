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
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.issue.IssueManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class SonarClearIssuesActionTest extends AbstractSonarLintLightTests {
  private DaemonCodeAnalyzer codeAnalyzer = mock(DaemonCodeAnalyzer.class);
  private IssueManager issueManager = mock(IssueManager.class);
  private AnActionEvent event = mock(AnActionEvent.class);

  private SonarClearIssuesAction clearIssues = new SonarClearIssuesAction(null, null, null);
  private VirtualFile file;

  @Before
  public void prepare() {
    replaceProjectComponent(DaemonCodeAnalyzer.class, codeAnalyzer);
    replaceProjectComponent(IssueManager.class, issueManager);
    when(event.getProject()).thenReturn(getProject());
    file = myFixture.copyFileToProject("foo.php", "foo.php");
  }

  @Test
  public void testClear() {
    FileEditorManager.getInstance(getProject()).openFile(file, true);

    clearIssues.actionPerformed(event);

    verify(codeAnalyzer).restart(any());
    verify(issueManager).clear();
  }

  @Test
  public void testClearWithInvalidFiles() throws IOException {
    FileEditorManager.getInstance(getProject()).openFile(file, true);

    WriteAction.run(() -> {
      file.delete(getProject());
    });

    clearIssues.actionPerformed(event);

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
