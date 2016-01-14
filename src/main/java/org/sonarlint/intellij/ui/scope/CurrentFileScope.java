/**
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
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.project.Project;

public class CurrentFileScope extends IssueTreeScope {
  private final Project project;

  public CurrentFileScope(Project project) {
    this.project = project;
    this.condition = selectedFileCondition();
    initEventHandling();
  }

  @Override public String getDisplayName() {
    return "Current File";
  }

  private void initEventHandling() {
    EditorChangeListener editorChangeListener = new EditorChangeListener();
    project.getMessageBus().connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, editorChangeListener);
  }

  private class EditorChangeListener extends FileEditorManagerAdapter {
    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
      condition = selectedFileCondition();

      for(ScopeListener l : listeners) {
        l.conditionChanged();
      }
    }
  }

  private Condition<VirtualFile> selectedFileCondition() {
    VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();

    if(selectedFiles.length == 0) {
      return new RejectAllCondition();
    }

    return new AcceptFileCondition(selectedFiles[0]);
  }

  private static class AcceptFileCondition implements Condition<VirtualFile> {
    private final VirtualFile file;
    AcceptFileCondition(VirtualFile file) {
      this.file = file;
    }

    @Override public boolean value(VirtualFile virtualFile) {
      return file.equals(virtualFile);
    }
  }

  private static class RejectAllCondition implements Condition<VirtualFile> {
    @Override public boolean value(VirtualFile virtualFile) {
      return false;
    }
  }

}
