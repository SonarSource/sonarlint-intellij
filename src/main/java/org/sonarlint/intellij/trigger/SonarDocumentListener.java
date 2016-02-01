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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.sonarlint.intellij.analysis.SonarLintAnalyzer;
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings;
import org.sonarlint.intellij.messages.TaskListener;
import org.sonarlint.intellij.util.SonarLintUtils;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ThreadSafe
public class SonarDocumentListener extends AbstractProjectComponent implements DocumentListener {
  private static final int DEFAULT_TIMER_MS = 2000;

  private final SonarLintGlobalSettings globalSettings;
  private final SonarLintAnalyzer analyzer;

  // entries in this map mean that the file is "dirty"
  private final Map<VirtualFile, Long> eventMap;
  private final EventWatcher watcher;
  private final int timerMs;

  public SonarDocumentListener(Project project, SonarLintGlobalSettings globalSettings, SonarLintAnalyzer analyzer, EditorFactory editorFactory) {
    this(project, globalSettings, analyzer, editorFactory, DEFAULT_TIMER_MS);
  }

  public SonarDocumentListener(Project project, SonarLintGlobalSettings globalSettings, SonarLintAnalyzer analyzer, EditorFactory editorFactory, int timerMs) {
    super(project);
    this.analyzer = analyzer;
    this.eventMap = new ConcurrentHashMap<>();
    this.globalSettings = globalSettings;
    this.watcher = new EventWatcher();
    this.timerMs = timerMs;

    editorFactory.getEventMulticaster().addDocumentListener(this);

    project.getMessageBus().connect(project).subscribe(TaskListener.SONARLINT_TASK_TOPIC, new TaskListener() {
      @Override public void started(SonarLintAnalyzer.SonarLintJob job) {
        removeFiles(job.files());
      }

      @Override public void ended(SonarLintAnalyzer.SonarLintJob job) {
        // nothing to do
      }
    });
  }

  @Override
  public void initComponent() {
    watcher.start();
  }

  public boolean hasEvents() {
    return !eventMap.isEmpty();
  }

  @Override public void beforeDocumentChange(DocumentEvent event) {
    //nothing to do
  }

  @Override public void documentChanged(DocumentEvent event) {
    if(!globalSettings.isAutoTrigger()) {
      return;
    }

    VirtualFile file = FileDocumentManager.getInstance().getFile(event.getDocument());
    Project project = ProjectUtil.guessProjectForFile(file);

    if (file == null || project == null || !project.equals(myProject)) {
      return;
    }

    postEvent(file);
  }

  @VisibleForTesting
  void postEvent(VirtualFile file) {
    eventMap.put(file, System.currentTimeMillis());
  }

  /**
   * Marks a file as launched, resetting its state to unchanged
   */
  public void removeFiles(Collection<VirtualFile> files) {
    for (VirtualFile f : files) {
      eventMap.remove(f);
    }
  }

  private void triggerFile(VirtualFile file) {
    if (!globalSettings.isAutoTrigger() || myProject.isDisposed()) {
      return;
    }

    Module m = ModuleUtil.findModuleForFile(file, myProject);
    if (!SonarLintUtils.shouldAnalyze(file, m)) {
      return;
    }

    analyzer.submitAsync(m, Collections.singleton(file));
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
  public void disposeComponent() {
    watcher.stopWatcher();
    eventMap.clear();
  }
}
