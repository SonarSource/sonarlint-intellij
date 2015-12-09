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
import org.sonarlint.intellij.issue.IssueProcessor;
import org.sonarlint.intellij.ui.SonarLintConsole;
import org.sonarlint.intellij.util.SonarLintUtils;

import java.util.Collection;

public class SonarlintAnalyzer extends AbstractProjectComponent {
  private static final Logger LOGGER = Logger.getInstance(SonarlintAnalyzer.class);
  private final IssueProcessor processor;

  public SonarlintAnalyzer(Project project, IssueProcessor processor) {
    super(project);
    this.processor = processor;
  }

  /**
   * Runs SonarLint analysis synchronously, if no analysis is already on going.
   * It might queue the submission of the job in the EDT thread.
   * Once it starts, it will display a ProgressWindow with the EDT and run the analysis in a pooled thread.
   * The reason why we might want to queue the analysis instead of starting immediately is that the EDT might currently hold a write access.
   * If we hold a write lock, the ApplicationManager will not work as expected, because it won't start a pooled thread if we hold
   * a write access (the pooled thread would dead lock if it needs read access). The listener for file editor events holds the write access, for example.
   * @see #submitAsync(Module, Collection)
   */
  public void submit(Module m, Collection<VirtualFile> files) {
    if (!tryRun()) {
      return;
    }

    final SonarLintJob job = new SonarLintJob(m, files);
    final SonarLintTask task = SonarLintTask.createForeground(processor, job);

    Application app = ApplicationManager.getApplication();

    if (!app.isDispatchThread() || app.isWriteAccessAllowed()) {
      app.invokeLater(new Runnable() {
        @Override
        public void run() {
          SonarLintUtils.saveFiles(job.files());
          ProgressManager.getInstance().run(task);
        }
      });
    } else {
      SonarLintUtils.saveFiles(job.files());
      ProgressManager.getInstance().run(task);
    }
  }

  private boolean tryRun() {
    boolean shouldRun = SonarLintStatus.get(this.myProject).tryRun();
    if (!shouldRun) {
      String msg = "Not submitting SonarLint analysis as one is already running";
      SonarLintConsole.getSonarQubeConsole(this.myProject).info(msg);
      LOGGER.info(msg);
    }
    return shouldRun;
  }

  /**
   * Runs SonarLint analysis asynchronously, in another thread.
   * It won't block the current thread (in most cases, the event dispatch thread), but the contents of file being analyzed
   * might be changed with the editor at the same time, resulting in a bad placement of the issues in the editor.
   * @see #submit(Module, Collection)
   */
  public void submitAsync(Module m, Collection<VirtualFile> files) {
    if (!tryRun()) {
      return;
    }
    final SonarLintJob job = new SonarLintJob(m, files);
    SonarLintTask task = SonarLintTask.createBackground(processor, job);
    SonarLintUtils.saveFiles(job.files());
    ProgressManager.getInstance().run(task);
  }

  public static class SonarLintJob {
    private final Module m;
    private final Collection<VirtualFile> files;

    SonarLintJob(Module m, Collection<VirtualFile> files) {
      this.m = m;
      this.files = files;
    }

    public Module module() {
      return m;
    }

    public Collection<VirtualFile> files() {
      return files;
    }
  }
}
