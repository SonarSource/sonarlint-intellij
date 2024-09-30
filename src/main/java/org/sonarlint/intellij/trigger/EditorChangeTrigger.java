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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.concurrent.ThreadSafe;
import org.sonarlint.intellij.messages.AnalysisListener;
import org.sonarlint.intellij.util.SonarLintAppUtils;

import static org.sonarlint.intellij.config.Settings.getGlobalSettings;

@ThreadSafe
@Service(Service.Level.PROJECT)
public final class EditorChangeTrigger implements DocumentListener, Disposable {

  // entries in this map mean that the file is "dirty"
  private final ConcurrentHashMap<VirtualFile, Long> eventMap = new ConcurrentHashMap<>();
  private final EventWatcher watcher;
  private final Project myProject;

  public EditorChangeTrigger(Project project) {
    myProject = project;
    watcher = new EventWatcher(myProject, "change", eventMap, TriggerType.EDITOR_CHANGE);
  }

  public void onProjectOpened() {
    myProject.getMessageBus()
      .connect()
      .subscribe(AnalysisListener.TOPIC, new AnalysisListener.Adapter() {
        @Override
        public void started(Collection<VirtualFile> files, TriggerType trigger) {
          removeFiles(files);
        }
      });
    watcher.start();
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(this, this);
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
    var file = FileDocumentManager.getInstance().getFile(event.getDocument());
    if (file == null) {
      return;
    }
    var project = SonarLintAppUtils.guessProjectForFile(file);

    if (project == null || !project.equals(myProject)) {
      return;
    }

    eventMap.put(file, System.currentTimeMillis());
  }

  /**
   * Marks a file as launched, resetting its state to unchanged
   */
  private void removeFiles(Collection<VirtualFile> files) {
    files.forEach(eventMap::remove);
  }

  Map<VirtualFile, Long> getEvents() {
    return Collections.unmodifiableMap(eventMap);
  }

  @Override
  public void dispose() {
    eventMap.clear();
    watcher.stopWatcher();
  }

}
