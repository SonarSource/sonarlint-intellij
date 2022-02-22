/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
package org.sonarlint.intellij.editor;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.lang.Language;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class CodeAnalyzerRestarterTest extends AbstractSonarLintLightTests {
  private PsiManager psiManager = mock(PsiManager.class);
  private DaemonCodeAnalyzer codeAnalyzer = mock(DaemonCodeAnalyzer.class);
  private FileEditorManager fileEditorManager = mock(FileEditorManager.class);
  private MessageBus bus = mock(MessageBus.class);
  private CodeAnalyzerRestarter analyzerRestarter;

  @Before
  public void prepare() {
    var connection = mock(MessageBusConnection.class);
    when(bus.connect(getProject())).thenReturn(connection);
    analyzerRestarter = new CodeAnalyzerRestarter(getProject(), codeAnalyzer);
  }

  @Test
  public void should_not_restart_invalid() {
    var vFile1 = mock(VirtualFile.class);
    when(vFile1.isValid()).thenReturn(false);

    when(fileEditorManager.getOpenFiles()).thenReturn(new VirtualFile[] {vFile1});

    analyzerRestarter.refreshOpenFiles();
    verifyZeroInteractions(codeAnalyzer);
    verifyZeroInteractions(psiManager);
  }

  @Test
  public void should_restart_all_open() {
    var file1 = createAndOpenTestPsiFile("Foo.java", Language.findLanguageByID("JAVA"), "public class Foo {}");
    var file2 = createAndOpenTestPsiFile("Bar.java", Language.findLanguageByID("JAVA"), "public class Bar {}");

    analyzerRestarter.refreshOpenFiles();

    verify(codeAnalyzer).restart(file1);
    verify(codeAnalyzer).restart(file2);
    verifyNoMoreInteractions(codeAnalyzer);
  }

  @Test
  public void should_restart_files() {
    var file1 = createAndOpenTestPsiFile("Foo.java", Language.findLanguageByID("JAVA"), "public class Foo {}");
    var file2 = createTestPsiFile("Bar.java", Language.findLanguageByID("JAVA"), "public class Bar {}");

    analyzerRestarter.refreshFiles(List.of(file1.getVirtualFile(), file2.getVirtualFile()));

    verify(codeAnalyzer).restart(file1);
    verifyNoMoreInteractions(codeAnalyzer);
  }
}
