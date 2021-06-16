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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import java.util.Collection;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.messages.TaskListener;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.util.SonarLintUtils;

public class SonarLintJobManager {
  private final MessageBus messageBus;
  private final Project myProject;

  public SonarLintJobManager(Project project) {
    this.messageBus = project.getMessageBus();
    myProject = project;
  }

  /**
   * Runs SonarLint analysis asynchronously, as a background task, in the application's thread pool.
   * It might queue the submission of the job in the thread pool.
   * It won't block the current thread (in most cases, the event dispatch thread), but the contents of the file being analyzed
   * might be changed with the editor at the same time, resulting in a bad or failed placement of the issues in the editor.
   *
   * @see #submitManual(Collection, TriggerType, boolean, AnalysisCallback)
   * @return
   */
  public SonarLintTask submitBackground(Collection<VirtualFile> files, TriggerType trigger, AnalysisCallback callback) {
    SonarLintJob newJob = new SonarLintJob(myProject, files, trigger, false, callback);
    SonarLintConsole console = SonarLintUtils.getService(myProject, SonarLintConsole.class);
    console.debug(String.format("[%s] %d file(s) submitted", trigger.getName(), newJob.files().size()));
    SonarLintTask task = new SonarLintTask(newJob, true);
    notifyStart(task.getJob());
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
  public SonarLintTask submitManual(Collection<VirtualFile> files, TriggerType trigger, boolean modal, AnalysisCallback callback) {
    SonarLintStatus status = SonarLintUtils.getService(myProject, SonarLintStatus.class);
    SonarLintConsole console = SonarLintUtils.getService(myProject, SonarLintConsole.class);
    if (myProject.isDisposed() || !status.tryRun()) {
      console.info("Canceling analysis triggered by the user because another one is already running or because the project is disposed");
      return null;
    }

    SonarLintJob newJob = new SonarLintJob(myProject, files, trigger, true, callback);
    console.debug(String.format("[%s] %d file(s) submitted", trigger.getName(), newJob.files().size()));
    SonarLintUserTask task = new SonarLintUserTask(newJob, modal);
    notifyStart(task.getJob());
    task.queue();
    return task;
  }

  private void notifyStart(SonarLintJob job) {
    messageBus.syncPublisher(TaskListener.SONARLINT_TASK_TOPIC).started(job);
  }

}
