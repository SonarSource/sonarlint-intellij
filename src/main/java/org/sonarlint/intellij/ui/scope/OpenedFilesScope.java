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
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class OpenedFilesScope extends IssueTreeScope {
  private final Project project;

  public OpenedFilesScope(Project project) {
    this.project = project;
    project.getMessageBus().connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new EditorChangeListener());
  }

  @Override
  public String getDisplayName() {
    return "Opened files";
  }

  @Override
  public Collection<VirtualFile> getAll() {
    FileEditorManager editorManager = FileEditorManager.getInstance(project);
    VirtualFile[] openFiles = editorManager.getOpenFiles();
    return Arrays.asList(openFiles);
  }

  private class EditorChangeListener extends FileEditorManagerAdapter {
    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      refreshCondition(source);
    }

    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      refreshCondition(source);
    }

    private void refreshCondition(@NotNull FileEditorManager editorManager) {
      VirtualFile[] openFiles = editorManager.getOpenFiles();
      filePredicate = new OpenedFilePredicate(Arrays.asList(openFiles));
      listeners.forEach(ScopeListener::conditionChanged);
    }
  }

  private static class OpenedFilePredicate implements Predicate<VirtualFile> {
    private final List<VirtualFile> openedFiles;

    OpenedFilePredicate(List<VirtualFile> openedFiles) {
      this.openedFiles = openedFiles;
    }

    @Override
    public boolean test(VirtualFile virtualFile) {
      return openedFiles.contains(virtualFile);
    }
  }
}
