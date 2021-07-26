/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.issue.IssueManager;
import org.sonarlint.intellij.messages.IssueStoreListener;
import org.sonarlint.intellij.messages.StatusListener;

public class CurrentFileController implements Disposable {
  private static final int DEFAULT_DELAY_MS = 300;
  private final Project project;
  private final IssueManager store;
  private SonarLintIssuesPanel panel;
  private AtomicLong refreshTimestamp = new AtomicLong(Long.MAX_VALUE);
  private final EventWatcher watcher;
  private final int delayMs = DEFAULT_DELAY_MS;
  private VirtualFile selectedFile = null;

  public CurrentFileController(Project project, IssueManager store) {
    this.project = project;
    this.store = store;
    this.watcher = new EventWatcher();
  }

  public void setPanel(SonarLintIssuesPanel panel) {
    this.panel = panel;
    initEventHandling();
    this.selectedFile = SonarLintUtils.getSelectedFile(project);
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

  @Override
  public void dispose() {
    watcher.stopWatcher();
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
      @Override
      public void filesChanged(final Set<VirtualFile> changedFiles) {
        if (selectedFile != null && changedFiles.contains(selectedFile)) {
          refreshTimestamp.set(System.currentTimeMillis() + delayMs);
        }
      }

      @Override
      public void allChanged() {
        if (selectedFile != null) {
          refreshTimestamp.set(System.currentTimeMillis() + delayMs);
        }
      }
    });
    watcher.start();
  }

  private class EventWatcher extends Thread {

    private boolean stop = false;

    EventWatcher() {
      this.setDaemon(true);
      this.setName("sonarlint-issue-panel-refresh");
    }

    public void stopWatcher() {
      stop = true;
      this.interrupt();
    }

    @Override
    public void run() {
      while (!stop) {
        checkRefresh();
        try {
          Thread.sleep(200);
        } catch (InterruptedException e) {
          // continue until stop flag is set
        }
      }
    }

    private void checkRefresh() {
      long t = System.currentTimeMillis();
      if (t > refreshTimestamp.get()) {
        refreshTimestamp.set(Long.MAX_VALUE);
        ApplicationManager.getApplication().invokeLater(() -> {
          update();
        });
      }
    }

  }

  private class EditorChangeListener implements FileEditorManagerListener {
    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
      selectedFile = event.getNewFile();
      update();
    }
  }

  private void update() {
    String emptyText = getEmptyText(selectedFile);
    if (selectedFile == null) {
      panel.update(null, Collections.emptyList(), emptyText);
    } else {
      panel.update(selectedFile, store.getForFile(selectedFile), emptyText);
    }
  }
}
