/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.finding.persistence.FindingsManager;
import org.sonarlint.intellij.messages.FindingStoreListener;
import org.sonarlint.intellij.messages.StatusListener;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

public class CurrentFileController implements Disposable {
  private static final int DEFAULT_DELAY_MS = 300;
  private final Project project;
  private final CurrentFilePanel currentFilePanel;
  private final AtomicLong refreshTimestamp = new AtomicLong(Long.MAX_VALUE);
  private final EventWatcher watcher;
  private VirtualFile selectedFile;

  public CurrentFileController(Project project, CurrentFilePanel currentFilePanel) {
    this.project = project;
    this.currentFilePanel = currentFilePanel;
    this.watcher = new EventWatcher();
    initEventHandling();
    this.selectedFile = SonarLintUtils.getSelectedFile(project);
    update();
  }

  private String getEmptyText(@Nullable VirtualFile selectedFile) {
    if (selectedFile == null) {
      return "No file opened in the editor";
    } else if (getService(project, FindingsManager.class).neverAnalyzedSinceStartup(selectedFile)) {
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
    var editorChangeListener = new EditorChangeListener();
    project.getMessageBus()
      .connect(project)
      .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, editorChangeListener);

    var busConnection = project.getMessageBus().connect(project);
    busConnection.subscribe(StatusListener.SONARLINT_STATUS_TOPIC,
      newStatus -> ApplicationManager.getApplication().invokeLater(currentFilePanel::refreshToolbar));
    busConnection.subscribe(FindingStoreListener.SONARLINT_ISSUE_STORE_TOPIC, new FindingStoreListener() {
      @Override
      public void filesChanged(final Set<VirtualFile> changedFiles) {
        if (selectedFile != null && changedFiles.contains(selectedFile)) {
          refreshTimestamp.set(System.currentTimeMillis() + DEFAULT_DELAY_MS);
        }
      }

      @Override
      public void allChanged() {
        if (selectedFile != null) {
          refreshTimestamp.set(System.currentTimeMillis() + DEFAULT_DELAY_MS);
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
        ApplicationManager.getApplication().invokeLater(CurrentFileController.this::update);
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
    var emptyText = getEmptyText(selectedFile);
    if (selectedFile == null) {
      currentFilePanel.update(null, Collections.emptyList(), emptyText);
    } else {
      currentFilePanel.update(selectedFile, getService(project, FindingsManager.class).getIssuesForFile(selectedFile), emptyText);
    }
  }
}
