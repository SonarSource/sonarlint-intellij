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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.vfs.VirtualFile;
import org.sonarlint.intellij.editor.AccumulatorIssueListener;
import org.sonarlint.intellij.issue.IssueProcessor;
import org.sonarlint.intellij.messages.TaskListener;
import org.sonarlint.intellij.ui.SonarLintConsole;
import com.intellij.openapi.project.Project;

public class SonarLintTask extends Task.Backgroundable {
  private static final Logger LOGGER = Logger.getInstance(SonarLintAnalyzer.class);
  private final IssueProcessor processor;
  private final SonarLintAnalyzer.SonarLintJob job;
  private final boolean startInBackground;


  private SonarLintTask(IssueProcessor processor, SonarLintAnalyzer.SonarLintJob job, boolean background) {
    super(job.module().getProject(), "SonarLint Analysis", true);
    this.processor = processor;
    this.job = job;
    this.startInBackground = background;
  }

  public static SonarLintTask createBackground(IssueProcessor processor, SonarLintAnalyzer.SonarLintJob job) {
    return new SonarLintTask(processor, job, true);
  }

  public static SonarLintTask createForeground(IssueProcessor processor, SonarLintAnalyzer.SonarLintJob job) {
    return new SonarLintTask(processor, job, false);
  }

  @Override
  public boolean shouldStartInBackground() {
    return startInBackground;
  }

  private static void stopRun(SonarLintAnalyzer.SonarLintJob job) {
    TaskListener taskListener = job.module().getProject().getMessageBus().syncPublisher(TaskListener.SONARLINT_TASK_TOPIC);
    taskListener.ended(job);
  }

  private static String getFileName(VirtualFile file) {
    return file.getName();
  }

  @Override
  public void run(ProgressIndicator indicator) {
    Project p = job.module().getProject();
    SonarLintStatus status = SonarLintStatus.get(p);
    SonarLintAnalysisConfigurator configurator = p.getComponent(SonarLintAnalysisConfigurator.class);

    try {
      if (indicator.isCanceled() || status.isCanceled()) {
        return;
      }

      indicator.setIndeterminate(true);
      if (job.files().size() > 1) {
        indicator.setText("Running SonarLint Analysis for " + job.files().size() + " files");
      } else {
        indicator.setText("Running SonarLint Analysis for '" + getFileName(job.files().iterator().next()) + "'");
      }

      final AccumulatorIssueListener listener = new AccumulatorIssueListener();
      LOGGER.info(indicator.getText());

      CancelMonitor monitor = new CancelMonitor(indicator, status, Thread.currentThread());
      try {
        monitor.start();
        configurator.analyzeModule(job.module(), job.files(), listener);
        indicator.startNonCancelableSection();
      } finally {
        monitor.stopMonitor();
      }

      //last chance to cancel (to avoid the possibility of having interrupt flag set)
      if (indicator.isCanceled() || status.isCanceled()) {
        return;
      }

      LOGGER.info("SonarLint analysis done");

      indicator.setIndeterminate(false);
      indicator.setFraction(.9);
      indicator.setText("Creating SonarLint issues: " + listener.getIssues().size());

      processor.process(job, listener.getIssues());
    } catch (RuntimeException e) {
      // if cancelled, ignore any errors since they were most likely caused by the interrupt
      if (!indicator.isCanceled() && !status.isCanceled()) {
        throw e;
      }
    } finally {
      stopRun(job);
    }
  }

  private class CancelMonitor extends Thread {
    private final ProgressIndicator indicator;
    private final SonarLintStatus status;
    private final Thread t;
    private boolean stop = false;

    public CancelMonitor(ProgressIndicator indicator, SonarLintStatus status, Thread t) {
      this.indicator = indicator;
      this.status = status;
      this.t = t;
      this.setName("sonarlint-cancel-monitor");
      this.setDaemon(true);
    }

    public synchronized void stopMonitor() {
      stop = true;
    }

    @Override
    public void run() {
      while (true) {
        synchronized (this) {
          // don't trust too much in isAlive: thread is probably pooled in a executor
          if (stop || !t.isAlive() || !status.isRunning()) {
            break;
          }
        }
        if (indicator.isCanceled() || status.isCanceled()) {
          // ensure that UI is canceled
          if (!indicator.isCanceled()) {
            indicator.cancel();
          }

          SonarLintConsole.get(myProject).info("Canceling...");
          t.interrupt();
          break;
        }

        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          break;
        }
      }
    }
  }
}
