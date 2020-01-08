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
package org.sonarlint.intellij.editor;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class CodeAnalyzerRestarterTest extends SonarTest {
  private Project project = mock(Project.class);
  private PsiManager psiManager = mock(PsiManager.class);
  private DaemonCodeAnalyzer codeAnalyzer = mock(DaemonCodeAnalyzer.class);
  private FileEditorManager fileEditorManager = mock(FileEditorManager.class);
  private MessageBus bus = mock(MessageBus.class);
  private CodeAnalyzerRestarter analyzerRestarter = new CodeAnalyzerRestarter(project, fileEditorManager, codeAnalyzer, psiManager, bus);

  @Before
  public void prepare() {
    MessageBusConnection connection = mock(MessageBusConnection.class);
    when(bus.connect(project)).thenReturn(connection);
  }

  @Test
  public void should_not_restart_invalid() {
    VirtualFile vFile1 = mock(VirtualFile.class);
    when(vFile1.isValid()).thenReturn(false);

    when(fileEditorManager.getOpenFiles()).thenReturn(new VirtualFile[] {vFile1});

    analyzerRestarter.refreshAllFiles();
    verifyZeroInteractions(codeAnalyzer);
    verifyZeroInteractions(psiManager);
  }

  @Test
  public void should_subscribe_on_init() {
    analyzerRestarter.initComponent();
    verify(bus).connect(project);
  }

  @Test
  public void should_restart_all_open() {
    VirtualFile vFile1 = mock(VirtualFile.class);
    when(vFile1.isValid()).thenReturn(true);
    PsiFile psiFile1 = mock(PsiFile.class);
    VirtualFile vFile2 = mock(VirtualFile.class);
    when(vFile2.isValid()).thenReturn(true);
    PsiFile psiFile2 = mock(PsiFile.class);

    when(psiManager.findFile(vFile1)).thenReturn(psiFile1);
    when(psiManager.findFile(vFile2)).thenReturn(psiFile2);

    when(fileEditorManager.getOpenFiles()).thenReturn(new VirtualFile[] {vFile1, vFile2});

    analyzerRestarter.refreshAllFiles();
    verify(codeAnalyzer).restart(psiFile1);
    verify(codeAnalyzer).restart(psiFile2);
    verifyNoMoreInteractions(codeAnalyzer);
  }

  @Test
  public void should_restart_files() {
    VirtualFile vFile1 = mock(VirtualFile.class);
    when(vFile1.isValid()).thenReturn(true);
    PsiFile psiFile1 = mock(PsiFile.class);
    VirtualFile vFile2 = mock(VirtualFile.class);
    when(vFile2.isValid()).thenReturn(true);
    PsiFile psiFile2 = mock(PsiFile.class);

    when(psiManager.findFile(vFile1)).thenReturn(psiFile1);
    when(psiManager.findFile(vFile2)).thenReturn(psiFile2);

    when(fileEditorManager.getOpenFiles()).thenReturn(new VirtualFile[] {vFile1});

    analyzerRestarter.refreshFiles(Arrays.asList(vFile1, vFile2));
    verify(codeAnalyzer).restart(psiFile1);
    verifyNoMoreInteractions(codeAnalyzer);
  }
}
