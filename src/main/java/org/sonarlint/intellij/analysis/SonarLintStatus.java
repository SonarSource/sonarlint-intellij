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

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import javax.annotation.concurrent.ThreadSafe;
import org.sonarlint.intellij.messages.StatusListener;

@ThreadSafe
public class SonarLintStatus extends AbstractProjectComponent {
  private final StatusListener statusListener;
  private Status status = Status.STOPPED;

  public SonarLintStatus(Project project) {
    super(project);
    this.statusListener = project.getMessageBus().syncPublisher(StatusListener.SONARLINT_STATUS_TOPIC);
  }

  public enum Status {RUNNING, STOPPED, CANCELLING}

  public static SonarLintStatus get(Project p) {
    return p.getComponent(SonarLintStatus.class);
  }

  /**
   * Whether a manually-initiated task is running.
   * Used, for example, to enable/disable task-related actions (run, stop).
   */
  public synchronized boolean isRunning() {
    return status == Status.RUNNING || status == Status.CANCELLING;
  }

  public synchronized boolean isCanceled() {
    return status == Status.CANCELLING;
  }

  public void stopRun() {
    Status callback = null;
    synchronized (this) {
      if (isRunning()) {
        status = Status.STOPPED;
        callback = status;
      }
    }

    //don't lock while calling listeners
    if (callback != null) {
      statusListener.changed(callback);
    }
  }

  /**
   * Cancel the task currently running
   */
  public void cancel() {
    Status callback = null;
    synchronized (this) {
      if (status == Status.RUNNING) {
        status = Status.CANCELLING;
        callback = Status.CANCELLING;
      }
    }

    //don't lock while calling listeners
    if (callback != null) {
      statusListener.changed(callback);
    }
  }

  public boolean tryRun() {
    Status callback = null;
    synchronized (this) {
      if (!isRunning()) {
        status = Status.RUNNING;
        callback = Status.RUNNING;
      }
    }

    //don't lock while calling listeners
    if (callback != null) {
      statusListener.changed(callback);
      return true;
    } else {
      return false;
    }
  }
}
