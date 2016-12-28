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
package org.sonarlint.intellij.ui.scope;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.function.Predicate;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class CurrentFileScopeTest extends SonarTest {
  @Test
  public void testAll() {
    VirtualFile file = mock(VirtualFile.class);
    mockOpenFile(file);
    CurrentFileScope scope = new CurrentFileScope(project);
    assertThat(scope.getAll()).containsOnly(file);
  }

  @Test
  public void testNoFileOpen() {
    mockNoOpenFile();
    CurrentFileScope scope = new CurrentFileScope(project);

    Predicate<VirtualFile> condition = scope.getCondition();
    assertThat(condition.test(mock(VirtualFile.class))).isFalse();
    assertThat(scope.getAll()).isEmpty();
  }

  @Test
  public void testCondition() {
    VirtualFile file = mock(VirtualFile.class);
    mockOpenFile(file);
    CurrentFileScope scope = new CurrentFileScope(project);

    assertThat(scope.getDisplayName()).isEqualTo("Current File");
    assertThat(scope.toString()).isEqualTo("Current File");
    Predicate<VirtualFile> condition = scope.getCondition();
    assertThat(condition.test(file)).isTrue();
    assertThat(condition.test(mock(VirtualFile.class))).isFalse();
  }

  @Test
  public void testListener() {
    VirtualFile file = mock(VirtualFile.class);
    mockOpenFile(file);
    CurrentFileScope scope = new CurrentFileScope(project);
    AbstractScope.ScopeListener listener = mock(AbstractScope.ScopeListener.class);
    scope.addListener(listener);
    scope.updateCondition(f -> true);
    verify(listener).conditionChanged();

    scope.removeListeners();
    scope.updateCondition(f -> true);
    verifyNoMoreInteractions(listener);
  }

  private void mockNoOpenFile() {
    FileEditorManager editorManager = mock(FileEditorManager.class);
    super.register(project, FileEditorManager.class, editorManager);
    when(editorManager.getSelectedTextEditor()).thenReturn(null);
  }

  private void mockOpenFile(VirtualFile file) {
    FileEditorManager editorManager = mock(FileEditorManager.class);
    FileDocumentManager docManager = mock(FileDocumentManager.class);

    super.register(project, FileEditorManager.class, editorManager);
    super.register(app, FileDocumentManager.class, docManager);

    Editor editor = mock(Editor.class);
    Document doc = mock(Document.class);
    when(editorManager.getSelectedTextEditor()).thenReturn(editor);
    when(editor.getDocument()).thenReturn(doc);
    when(docManager.getFile(doc)).thenReturn(file);
  }
}
