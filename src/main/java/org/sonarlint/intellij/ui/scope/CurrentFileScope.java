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

import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.util.SonarLintUtils;

public class CurrentFileScope extends IssueTreeScope {
  private final Project project;

  public CurrentFileScope(Project project) {
    this.project = project;
    this.filePredicate = selectedFileCondition();
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
      filePredicate = selectedFileCondition();
      listeners.forEach(ScopeListener::conditionChanged);
    }
  }

  @Override
  public Collection<VirtualFile> getAll() {
    VirtualFile selectedFile = SonarLintUtils.getSelectedFile(project);
    if(selectedFile != null) {
      return Collections.singleton(selectedFile);
    } else {
      return Collections.emptySet();
    }
  }

  private Predicate<VirtualFile> selectedFileCondition() {
    VirtualFile file = SonarLintUtils.getSelectedFile(project);

    if (file == null) {
      return f -> false;
    }

    return new AcceptFilePredicate(file);
  }

  private static class AcceptFilePredicate implements Predicate<VirtualFile> {
    private final VirtualFile file;

    AcceptFilePredicate(VirtualFile file) {
      this.file = file;
    }

    @Override public boolean test(VirtualFile virtualFile) {
      return file.equals(virtualFile);
    }
  }
}
