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
package org.sonarlint.intellij.trigger;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.analysis.SonarLintJobManager;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.issue.IssueStore;
import org.sonarlint.intellij.util.SonarLintUtils;

public class FileEditorTrigger extends AbstractProjectComponent implements FileEditorManagerListener {
  private final IssueStore store;
  private final SonarLintJobManager analyzer;
  private final SonarLintGlobalSettings globalSettings;
  private final MessageBusConnection busConnection;

  public FileEditorTrigger(Project project, IssueStore store, SonarLintJobManager analyzer, SonarLintGlobalSettings globalSettings) {
    super(project);
    this.store = store;
    this.analyzer = analyzer;
    this.globalSettings = globalSettings;
    this.busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this);
  }

  @Override
  /**
   * I've tried hard to group opening events on startup without success.
   * Tried: Project.isInitialized, Project.isOpen, schedule to EDT thread and to WriteAction.
   * So on startup, opened files will be submitted one by one.
   */
  public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    if (!globalSettings.isAutoTrigger()) {
      return;
    }

    Module m = ModuleUtil.findModuleForFile(file, myProject);
    if (m == null || !SonarLintUtils.shouldAnalyzeAutomatically(file, m)) {
      return;
    }

    analyzer.submitAsync(m, Collections.singleton(file), TriggerType.EDITOR_OPEN);
  }

  @Override
  /**
   * Removes issues from the store that are located in the file that was closed.
   */
  public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    if (myProject.isDisposed()) {
      // don't risk do anything here, the store will be gone anyway
      return;
    }

    AccessToken token = ReadAction.start();
    try {
      store.clean(file);
    } finally {
      // closeable only introduced in 2016.2
      token.finish();
    }
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    // nothing to do
  }

  @Override
  public void disposeComponent() {
    busConnection.disconnect();
  }
}
