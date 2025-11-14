/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.AbstractSonarLintLightTests;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CodeAnalyzerRestarterTests extends AbstractSonarLintLightTests {
  private PsiManager psiManager = mock(PsiManager.class);
  private DaemonCodeAnalyzer codeAnalyzer = mock(DaemonCodeAnalyzer.class);
  private FileEditorManager fileEditorManager = mock(FileEditorManager.class);
  private MessageBus bus = mock(MessageBus.class);
  private CodeAnalyzerRestarter analyzerRestarter;

  @BeforeEach
  void prepare() {
    var connection = mock(MessageBusConnection.class);
    when(bus.connect(getProject())).thenReturn(connection);
    analyzerRestarter = new CodeAnalyzerRestarter(getProject(), codeAnalyzer);
  }

  @Test
  void should_not_restart_invalid() {
    var vFile1 = mock(VirtualFile.class);
    when(vFile1.isValid()).thenReturn(false);

    when(fileEditorManager.getOpenFiles()).thenReturn(new VirtualFile[] {vFile1});

    analyzerRestarter.refreshOpenFiles();
    verifyNoInteractions(codeAnalyzer);
    verifyNoInteractions(psiManager);
  }

  @Test
  void should_restart_all_open() {
    var file1 = createAndOpenTestPsiFile("Foo.java", Language.findLanguageByID("JAVA"), "class Foo {}");
    var file2 = createAndOpenTestPsiFile("Bar.java", Language.findLanguageByID("JAVA"), "class Bar {}");

    analyzerRestarter.refreshOpenFiles();

    verify(codeAnalyzer, timeout(1000)).restart(file1);
    verify(codeAnalyzer).restart(file2);
    verifyNoMoreInteractions(codeAnalyzer);
  }

  @Test
  void should_restart_files() {
    var file1 = createAndOpenTestPsiFile("Foo.java", Language.findLanguageByID("JAVA"), "class Foo {}");
    var file2 = createTestPsiFile("Bar.java", Language.findLanguageByID("JAVA"), "class Bar {}");

    analyzerRestarter.refreshFiles(List.of(file1.getVirtualFile(), file2.getVirtualFile()));

    verify(codeAnalyzer, timeout(1000)).restart(file1);
    verifyNoMoreInteractions(codeAnalyzer);
  }
}
