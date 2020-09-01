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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.annotation.concurrent.ThreadSafe;
import org.sonarlint.intellij.analysis.SonarLintJob;
import org.sonarlint.intellij.messages.TaskListener;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarlint.intellij.util.SonarLintUtils;

import static org.sonarlint.intellij.config.Settings.getGlobalSettings;

@ThreadSafe
public class EditorChangeTrigger implements DocumentListener, Disposable {
  private static final int DEFAULT_TIMER_MS = 2000;

  // entries in this map mean that the file is "dirty"
  private final Map<VirtualFile, Long> eventMap = new ConcurrentHashMap<>();
  private final EventWatcher watcher;
  private final int timerMs = DEFAULT_TIMER_MS;
  private final Project myProject;

  public EditorChangeTrigger(Project project) {
    myProject = project;
    watcher = new EventWatcher();
  }

  public void onProjectOpened() {
    myProject.getMessageBus()
      .connect(myProject)
      .subscribe(TaskListener.SONARLINT_TASK_TOPIC, new TaskListener.Adapter() {
        @Override
        public void started(SonarLintJob job) {
          removeFiles(job.allFiles());
        }
      });
    watcher.start();
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(this);
  }

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
    // nothing to do
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    if (!getGlobalSettings().isAutoTrigger()) {
      return;
    }
    VirtualFile file = FileDocumentManager.getInstance().getFile(event.getDocument());
    if (file == null) {
      return;
    }
    Project project = SonarLintAppUtils.guessProjectForFile(file);

    if (project == null || !project.equals(myProject)) {
      return;
    }

    eventMap.put(file, System.currentTimeMillis());
  }

  /**
   * Marks a file as launched, resetting its state to unchanged
   */
  public void removeFiles(Stream<VirtualFile> files) {
    files.forEach(eventMap::remove);
  }

  Map<VirtualFile, Long> getEvents() {
    return Collections.unmodifiableMap(eventMap);
  }

  private class EventWatcher extends Thread {

    private boolean stop = false;
    EventWatcher() {
      this.setDaemon(true);
      this.setName("sonarlint-auto-trigger-" + myProject.getName());
    }

    public void stopWatcher() {
      stop = true;
      this.interrupt();
    }

    @Override
    public void run() {
      while (!stop) {
        checkTimers();
        try {
          Thread.sleep(200);
        } catch (InterruptedException e) {
          // continue until stop flag is set
        }
      }
    }

    private void triggerFile(VirtualFile file) {
      if (SonarLintAppUtils.isOpenFile(myProject, file) && getGlobalSettings().isAutoTrigger()) {
        SonarLintSubmitter submitter = SonarLintUtils.getService(myProject, SonarLintSubmitter.class);
        submitter.submitFiles(Collections.singleton(file), TriggerType.EDITOR_CHANGE, true);
      }
    }

    private void checkTimers() {
      long t = System.currentTimeMillis();

      Iterator<Map.Entry<VirtualFile, Long>> it = eventMap.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<VirtualFile, Long> e = it.next();
        if (!e.getKey().isValid()) {
          it.remove();
          continue;
        }
        // don't trigger if file currently has errors?
        // filter files opened in the editor
        // use some heuristics based on analysis time or average pauses? Or make it configurable?
        if (e.getValue() + timerMs < t) {
          triggerFile(e.getKey());
          it.remove();
        }
      }
    }

  }

  @Override
  public void dispose() {
    EditorFactory.getInstance().getEventMulticaster().removeDocumentListener(this);
    eventMap.clear();
    watcher.stopWatcher();
  }

}
