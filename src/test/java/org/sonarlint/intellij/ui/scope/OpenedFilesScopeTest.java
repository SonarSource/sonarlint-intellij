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

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Before;
import org.junit.Test;
import org.sonarlint.intellij.SonarTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OpenedFilesScopeTest extends SonarTest {
  private OpenedFilesScope scope;
  private FileEditorManager editorManager;

  @Before
  public void setUp() {
    super.setUp();
    editorManager = mock(FileEditorManager.class);
    super.register(FileEditorManager.class, editorManager);
    scope = new OpenedFilesScope(project);
  }

  @Test
  public void testAll() {
    VirtualFile file = mock(VirtualFile.class);
    when(editorManager.getOpenFiles()).thenReturn(new VirtualFile[] {file});
    assertThat(scope.getAll()).containsOnly(file);
    assertThat(scope.getDisplayName()).isEqualTo("Opened files");
  }

  @Test
  public void testCondition() {
    AbstractScope.ScopeListener listener = mock(AbstractScope.ScopeListener.class);
    VirtualFile file = mock(VirtualFile.class);
    when(editorManager.getOpenFiles()).thenReturn(new VirtualFile[] {file});
    scope.addListener(listener);
    project.getMessageBus().syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER).fileOpened(editorManager, file);
    verify(listener).conditionChanged();
    assertThat(scope.getCondition().test(file)).isTrue();
    assertThat(scope.getCondition().test(mock(VirtualFile.class))).isFalse();
  }
}
