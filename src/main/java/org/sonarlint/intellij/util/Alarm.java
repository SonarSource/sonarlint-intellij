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
package org.sonarlint.intellij.util;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Alarm {
  private final Duration duration;
  private final Runnable endRunnable;
  private final ScheduledExecutorService executorService;
  private ScheduledFuture<?> scheduledFuture;

  public Alarm(String name, Duration duration, Runnable endRunnable) {
    this.duration = duration;
    this.endRunnable = endRunnable;
    executorService = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, name));
  }

  public void schedule() {
    // if already scheduled, don't re-schedule
    if (scheduledFuture == null) {
      scheduledFuture = executorService.schedule(this::notifyEnd, duration.toMillis(), TimeUnit.MILLISECONDS);
    }
  }

  public void reset() {
    cancelRunning();
    schedule();
  }

  private void notifyEnd() {
    if (!executorService.isShutdown()) {
      scheduledFuture = null;
      endRunnable.run();
    }
  }

  public void shutdown() {
    cancelRunning();
    executorService.shutdownNow();
  }

  private void cancelRunning() {
    if (scheduledFuture != null) {
      scheduledFuture.cancel(false);
    }
    scheduledFuture = null;
  }
}
