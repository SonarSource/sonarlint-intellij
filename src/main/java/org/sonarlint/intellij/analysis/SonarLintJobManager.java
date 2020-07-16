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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.messages.TaskListener;
import org.sonarlint.intellij.trigger.TriggerType;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintUtils;

public class SonarLintJobManager implements Disposable, ProjectManagerListener {
  private final ExecutorService executor = Executors.newSingleThreadExecutor(new AnalysisThreadFactory());
  private final MessageBus messageBus;
  private final Project myProject;

  public SonarLintJobManager(Project project) {
    this.messageBus = project.getMessageBus();
    myProject = project;
    Application application = ApplicationManager.getApplication();
    application.getMessageBus().connect(this).subscribe(ProjectManager.TOPIC, this);
  }

  /**
   * Runs SonarLint analysis asynchronously, as a background task, in the application's thread pool.
   * It might queue the submission of the job in the thread pool.
   * It won't block the current thread (in most cases, the event dispatch thread), but the contents of the file being analyzed
   * might be changed with the editor at the same time, resulting in a bad or failed placement of the issues in the editor.
   *
   * @see #submitManual(Map, Collection, TriggerType, boolean, AnalysisCallback)
   */
  public void submitBackground(Map<Module, Collection<VirtualFile>> files, Collection<VirtualFile> filesToClearIssues, TriggerType trigger, AnalysisCallback callback) {
    SonarLintJob newJob = new SonarLintJob(myProject, files, filesToClearIssues, trigger, false, callback);
    SonarLintConsole console = SonarLintUtils.getService(myProject, SonarLintConsole.class);
    console.debug(String.format("[%s] %d file(s) submitted", trigger.getName(), newJob.allFiles().count()));
    SonarLintTask task = new SonarLintTask(myProject, newJob, true);
    notifyStart(task.getJob());
    ProgressManager progressManager = ProgressManager.getInstance();
    executor.submit(() -> progressManager.run(task));
  }

  /**
   * Runs SonarLint analysis synchronously, if no manual (foreground) analysis is already on going.
   * If a foreground analysis is already on going, this method simply returns an empty AnalysisResult.
   * Once it starts, it will display a ProgressWindow with the EDT and run the analysis in a pooled thread.
   *
   * @see #submitBackground(Map, Collection, TriggerType, AnalysisCallback)
   */
  public void submitManual(Map<Module, Collection<VirtualFile>> files, Collection<VirtualFile> filesToClearIssues, TriggerType trigger, boolean modal,
    AnalysisCallback callback) {
    SonarLintStatus status = SonarLintUtils.getService(myProject, SonarLintStatus.class);
    SonarLintConsole console = SonarLintUtils.getService(myProject, SonarLintConsole.class);
    if (myProject.isDisposed() || !status.tryRun()) {
      console.info("Canceling analysis triggered by the user because another one is already running or because the project is disposed");
      return;
    }

    SonarLintJob newJob = new SonarLintJob(myProject, files, filesToClearIssues, trigger, true, callback);
    console.debug(String.format("[%s] %d file(s) submitted", trigger.getName(), newJob.allFiles().count()));
    SonarLintUserTask task = new SonarLintUserTask(myProject, newJob, modal);
    notifyStart(task.getJob());
    ProgressManager progressManager = ProgressManager.getInstance();
    if (modal) {
      progressManager.run(task);
    }
    else {
      executor.submit(() -> progressManager.run(task));
    }
  }

  private void notifyStart(SonarLintJob job) {
    messageBus.syncPublisher(TaskListener.SONARLINT_TASK_TOPIC).started(job);
  }

  @Override
  public void projectClosing(@NotNull Project project) {
    if (project.equals(myProject)) {
      executor.shutdownNow();
    }
  }

  @Override
  public void dispose() {
    executor.shutdownNow();
  }
}
