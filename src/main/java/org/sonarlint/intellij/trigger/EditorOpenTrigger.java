/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
package org.sonarlint.intellij.trigger;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.analysis.AnalysisSubmitter;
import org.sonarlint.intellij.core.ProjectBindingManager;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class EditorOpenTrigger implements FileEditorManagerListener, StartupActivity {

  @Override
  public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    getService(source.getProject(), AnalysisSubmitter.class).autoAnalyzeFile(file, TriggerType.EDITOR_OPEN);
  }

  @Override
  public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    // nothing to do
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    // nothing to do
  }

  @Override
  public void runActivity(@NotNull Project myProject) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      myProject.getMessageBus().connect(myProject).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this);
      // skip analyzing open files at startup when connected, it will be triggered after the sync
      if (!getService(myProject, ProjectBindingManager.class).isBindingValid()) {
        getService(myProject, AnalysisSubmitter.class).autoAnalyzeOpenFiles(TriggerType.EDITOR_OPEN);
      }
    }
  }
}
