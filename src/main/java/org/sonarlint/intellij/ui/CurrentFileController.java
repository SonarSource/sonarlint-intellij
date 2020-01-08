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
package org.sonarlint.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.messages.IssueStoreListener;
import org.sonarlint.intellij.messages.StatusListener;
import org.sonarlint.intellij.util.SonarLintUtils;

public class CurrentFileController {
  private final Project project;
  private final IssueManager store;
  private SonarLintIssuesPanel panel;

  public CurrentFileController(Project project, IssueManager store) {
    this.project = project;
    this.store = store;
  }

  public void setPanel(SonarLintIssuesPanel panel) {
    this.panel = panel;
    initEventHandling();
    update();
  }

  private String getEmptyText(@Nullable VirtualFile selectedFile) {
    if (selectedFile == null) {
      return "No file opened in the editor";
    } else if (store.getForFileOrNull(selectedFile) == null) {
      return "No analysis done on the current opened file";
    } else {
      return "No issues found in the current opened file";
    }
  }

  private void initEventHandling() {
    EditorChangeListener editorChangeListener = new EditorChangeListener();
    project.getMessageBus()
      .connect(project)
      .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, editorChangeListener);

    MessageBusConnection busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(StatusListener.SONARLINT_STATUS_TOPIC,
      newStatus -> ApplicationManager.getApplication().invokeLater(panel::refreshToolbar));
    busConnection.subscribe(IssueStoreListener.SONARLINT_ISSUE_STORE_TOPIC, new IssueStoreListener() {
      @Override public void filesChanged(final Map<VirtualFile, Collection<LiveIssue>> map) {
        ApplicationManager.getApplication().invokeLater(() -> {
          VirtualFile selectedFile = SonarLintUtils.getSelectedFile(project);
          if (selectedFile != null && map.containsKey(selectedFile)) {
            panel.update(selectedFile, map.get(selectedFile), getEmptyText(selectedFile));
          }
        });
      }

      @Override public void allChanged() {
        ApplicationManager.getApplication().invokeLater(() -> {
          VirtualFile selectedFile = SonarLintUtils.getSelectedFile(project);
          if (selectedFile != null) {
            panel.update(selectedFile, store.getForFile(selectedFile), getEmptyText(selectedFile));
          }
        });
      }
    });
  }

  private class EditorChangeListener extends FileEditorManagerAdapter {
    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
      update();
    }
  }

  private void update() {
    VirtualFile selectedFile = SonarLintUtils.getSelectedFile(project);
    String emptyText = getEmptyText(selectedFile);
    if (selectedFile == null) {
      panel.update(null, Collections.emptyList(), emptyText);
    } else {
      panel.update(selectedFile, store.getForFile(selectedFile), emptyText);
    }
  }
}
