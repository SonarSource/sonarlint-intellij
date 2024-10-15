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
import javax.annotation.concurrent.ThreadSafe;

import static org.sonarlint.intellij.config.Settings.getGlobalSettings;
import static org.sonarlint.intellij.util.SonarLintAppUtils.guessProjectForFile;

@ThreadSafe
@Service(Service.Level.PROJECT)
public final class EditorChangeTrigger implements DocumentListener, Disposable {

  private final EventScheduler scheduler;
  private final Project myProject;

  public EditorChangeTrigger(Project project) {
    myProject = project;
    scheduler = new EventScheduler(myProject, "change", TriggerType.EDITOR_CHANGE, 2000, false);
  }

  public void onProjectOpened() {
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
    var project = guessProjectForFile(file);

    if (project == null || !project.equals(myProject)) {
      return;
    }

    scheduler.notify(file);
  }

  @Override
  public void dispose() {
    scheduler.stopScheduler();
  }

}
