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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import org.sonarlint.intellij.issue.IssueProcessor;
import org.sonarlint.intellij.messages.TaskListener;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintUtils;

import java.util.HashSet;
import java.util.Set;

public class SonarLintAnalyzer extends AbstractProjectComponent {
  private static final Logger LOGGER = Logger.getInstance(SonarLintAnalyzer.class);
  private final IssueProcessor processor;
  private final MessageBus messageBus;
  private final JobQueue queue;
  // used to synchronize the handling of queue and running status together
  private final Object lock;
  private final SonarLintStatus status;

  public SonarLintAnalyzer(Project project, IssueProcessor processor) {
    super(project);
    this.processor = processor;
    this.messageBus = project.getMessageBus();
    this.queue = new JobQueue(project);
    this.lock = new Object();
    this.status = SonarLintStatus.get(this.myProject);

    messageBus.connect(project).subscribe(TaskListener.SONARLINT_TASK_TOPIC, new TaskListener() {
      @Override public void started(SonarLintJob job) {
        //nothing to do
      }

      @Override public void ended(SonarLintJob job) {
        taskFinished();
      }
    });
  }

  public void submitAsync(Module m, Set<VirtualFile> files) {
    SonarLintJob newJob = new SonarLintJob(m, files);
    SonarLintJob nextJob;

    synchronized (lock) {
      try {
        queue.queue(newJob);
      } catch (JobQueue.NoCapacityException e) {
        String msg = "Not submitting SonarLint analysis because job queue is full";
        SonarLintConsole.get(myProject).info(msg);
        LOGGER.warn(msg);
        return;
      }

      if (!status.tryRun()) {
        return;
      }

      nextJob = queue.get();
    }

    launchAsync(nextJob);
  }

  /**
   * Runs SonarLint analysis synchronously, if no analysis is already on going.
   * It might queue the submission of the job in the EDT thread.
   * Once it starts, it will display a ProgressWindow with the EDT and run the analysis in a pooled thread.
   * The reason why we might want to queue the analysis instead of starting immediately is that the EDT might currently hold a write access.
   * If we hold a write lock, the ApplicationManager will not work as expected, because it won't start a pooled thread if we hold
   * a write access (the pooled thread would dead lock if it needs read access). The listener for file editor events holds the write access, for example.
   * @see #submitAsync(Module, Set)
   */
  public void submit(Module m, Set<VirtualFile> files) {
    synchronized (lock) {
      if (myProject.isDisposed() || !status.tryRun()) {
        return;
      }
    }

    final SonarLintJob job = new SonarLintJob(m, files);
    final SonarLintTask task = SonarLintTask.createForeground(processor, job);
    saveAndRun(task, job);
  }

  /**
   * Runs SonarLint analysis asynchronously, in another thread.
   * It won't block the current thread (in most cases, the event dispatch thread), but the contents of file being analyzed
   * might be changed with the editor at the same time, resulting in a bad placement of the issues in the editor.
   * @see #submit(Module, Set)
   */
  private void launchAsync(final SonarLintJob job) {
    final SonarLintTask task = SonarLintTask.createBackground(processor, job);
    saveAndRun(task, job);
  }

  private void saveAndRun(final SonarLintTask task, final SonarLintJob job) {
    final Application app = ApplicationManager.getApplication();
    if (!app.isDispatchThread() || app.isWriteAccessAllowed()) {
      app.invokeLater(new Runnable() {
        @Override
        public void run() {
          // we save as late as possible, even if job was queued up for a while to get the most up-to-date results
          SonarLintUtils.saveFiles(job.files());
          notifyStart(job);
          ProgressManager.getInstance().run(task);
        }
      });
    } else {
      SonarLintUtils.saveFiles(job.files());
      notifyStart(job);
      ProgressManager.getInstance().run(task);
    }
  }

  public void taskFinished() {
    synchronized (lock) {
      if (queue.size() > 0) {
        // try launch next, if there is any, without changing running status
        SonarLintJob job = queue.get();
        launchAsync(job);
      } else {
        status.stopRun();
      }
    }
  }

  private void notifyStart(SonarLintJob job) {
    messageBus.syncPublisher(TaskListener.SONARLINT_TASK_TOPIC).started(job);
  }

  public static class SonarLintJob {
    private final Module m;
    private final Set<VirtualFile> files;
    private final long creationTime;

    SonarLintJob(Module m, Set<VirtualFile> files) {
      this.m = m;
      // make sure that it is not immutable so that it can be changed later
      this.files = new HashSet<>();
      this.files.addAll(files);
      this.creationTime = System.currentTimeMillis();
    }

    public long creationTime() {
      return creationTime;
    }

    public Module module() {
      return m;
    }

    /**
     * Set of files is not protected. It can be modified.
     */
    public Set<VirtualFile> files() {
      return files;
    }
  }
}
