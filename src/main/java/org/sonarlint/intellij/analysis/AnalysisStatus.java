/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import java.util.UUID;
import javax.annotation.concurrent.ThreadSafe;
import org.sonarlint.intellij.core.BackendService;
import org.sonarlint.intellij.messages.StatusListener;

import static org.sonarlint.intellij.common.util.SonarLintUtils.getService;

@ThreadSafe
@Service(Service.Level.PROJECT)
public final class AnalysisStatus {
  private final StatusListener statusListener;
  private Status status = Status.STOPPED;
  private UUID analysisId;

  public AnalysisStatus(Project project) {
    this.statusListener = project.getMessageBus().syncPublisher(StatusListener.SONARLINT_STATUS_TOPIC);
  }

  public enum Status {RUNNING, STOPPED}

  public static AnalysisStatus get(Project p) {
    return getService(p, AnalysisStatus.class);
  }

  /**
   * Whether a manually-initiated task is running.
   * Used, for example, to enable/disable task-related actions (run, stop).
   */
  public synchronized boolean isRunning() {
    return status == Status.RUNNING;
  }

  public void stopRun(UUID analysisId) {
    if (this.analysisId == analysisId) {
      stopRun();
    }
  }

  public void stopRun() {
    Status callback = null;
    synchronized (this) {
      if (isRunning()) {
        status = Status.STOPPED;
        callback = status;
      }
      if (analysisId != null) {
        getService(BackendService.class).cancelTask(analysisId.toString());
      }
      this.analysisId = null;
    }

    // don't lock while calling listeners
    if (callback != null) {
      statusListener.changed(callback);
    }
  }

  public boolean tryRun(UUID analysisId) {
    Status callback = null;
    synchronized (this) {
      if (!isRunning()) {
        status = Status.RUNNING;
        callback = Status.RUNNING;
        this.analysisId = analysisId;
      }
    }

    // don't lock while calling listeners
    if (callback != null) {
      statusListener.changed(callback);
      return true;
    } else {
      return false;
    }
  }
}
