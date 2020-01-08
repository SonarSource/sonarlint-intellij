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
package org.sonarlint.intellij.trigger;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Arrays;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;

public class EditorOpenTrigger extends AbstractProjectComponent implements FileEditorManagerListener {
  private final SonarLintSubmitter submitter;
  private final SonarLintGlobalSettings globalSettings;

  public EditorOpenTrigger(Project project, SonarLintSubmitter submitter, SonarLintGlobalSettings globalSettings) {
    super(project);
    this.submitter = submitter;
    this.globalSettings = globalSettings;
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      StartupManager.getInstance(myProject).registerPostStartupActivity(this::afterStartup);
    }
  }

  private void afterStartup() {
    myProject.getMessageBus().connect(myProject).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this);
    if (globalSettings.isAutoTrigger()) {
      VirtualFile[] openFiles = FileEditorManager.getInstance(myProject).getOpenFiles();
      if (openFiles.length > 0) {
        submitter.submitFiles(Arrays.asList(openFiles), TriggerType.EDITOR_OPEN, true);
      }
    }
  }

  @Override
  public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    if (!globalSettings.isAutoTrigger()) {
      return;
    }
    submitter.submitFiles(Collections.singleton(file), TriggerType.EDITOR_OPEN, true);
  }

  @Override
  public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    // nothing to do
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    // nothing to do
  }
}
