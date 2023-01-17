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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import java.util.Collection;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.common.util.SonarLintUtils;
import org.sonarlint.intellij.messages.AnalysisListener;
import org.sonarlint.intellij.trigger.TriggerType;

public class AnalysisManager {
  private final MessageBus messageBus;
  private final Project myProject;

  public AnalysisManager(Project project) {
    this.messageBus = project.getMessageBus();
    myProject = project;
  }

  /**
   * Runs SonarLint analysis asynchronously, as a background task, in the application's thread pool.
   * It might queue the submission of the task in the thread pool.
   * It won't block the current thread (in most cases, the event dispatch thread), but the contents of the file being analyzed
   * might be changed with the editor at the same time, resulting in a bad or failed placement of the issues in the editor.
   *
   * @see #submitManual(Collection, TriggerType, boolean, AnalysisCallback)
   * @return the newly submitted analysis task
   */
  public AnalysisTask submitBackground(Collection<VirtualFile> files, TriggerType trigger, AnalysisCallback callback) {
    var analysisRequest = new AnalysisRequest(myProject, files, trigger, false, callback);
    var console = SonarLintUtils.getService(myProject, SonarLintConsole.class);
    console.debug(String.format("[%s] %d file(s) submitted", trigger.getName(), analysisRequest.files().size()));
    var task = new AnalysisTask(analysisRequest, true);
    notifyStart(task.getRequest());
    task.queue();
    return task;
  }

  /**
   * Runs SonarLint analysis synchronously, if no manual (foreground) analysis is already on going.
   * If a foreground analysis is already on going, this method simply returns an empty AnalysisResult.
   * Once it starts, it will display a ProgressWindow with the EDT and run the analysis in a pooled thread.
   *
   * @see #submitBackground(Collection, TriggerType, AnalysisCallback)
   */
  @CheckForNull
  public AnalysisTask submitManual(Collection<VirtualFile> files, TriggerType trigger, boolean modal, AnalysisCallback callback) {
    var status = SonarLintUtils.getService(myProject, AnalysisStatus.class);
    var console = SonarLintUtils.getService(myProject, SonarLintConsole.class);
    if (myProject.isDisposed() || !status.tryRun()) {
      console.info("Canceling analysis triggered by the user because another one is already running or because the project is disposed");
      return null;
    }

    var analysisRequest = new AnalysisRequest(myProject, files, trigger, true, callback);
    console.debug(String.format("[%s] %d file(s) submitted", trigger.getName(), analysisRequest.files().size()));
    var task = new UserTriggeredAnalysisTask(analysisRequest, modal);
    notifyStart(task.getRequest());
    task.queue();
    return task;
  }

  private void notifyStart(AnalysisRequest request) {
    messageBus.syncPublisher(AnalysisListener.TOPIC).started(request);
  }

}
