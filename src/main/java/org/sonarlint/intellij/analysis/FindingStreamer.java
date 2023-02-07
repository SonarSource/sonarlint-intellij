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

import com.intellij.openapi.vfs.VirtualFile;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.sonarlint.intellij.finding.LiveFindings;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.finding.issue.LiveIssue;

public class FindingStreamer {
  public static final Duration STREAMING_INTERVAL = Duration.ofMillis(300);
  private final Alarm streamingTriggeringAlarm;
  private final Map<VirtualFile, Collection<LiveIssue>> issuesPerFile = new ConcurrentHashMap<>();
  private final Map<VirtualFile, Collection<LiveSecurityHotspot>> securityHotspotsPerFile = new ConcurrentHashMap<>();

  public FindingStreamer(AnalysisCallback analysisCallback) {
    this.streamingTriggeringAlarm = new Alarm(STREAMING_INTERVAL, () -> this.triggerStreaming(analysisCallback));
  }

  public void streamIssue(VirtualFile file, LiveIssue issue) {
    issuesPerFile.computeIfAbsent(file, k -> new ArrayList<>()).add(issue);
    streamingTriggeringAlarm.schedule();
  }

  public void streamSecurityHotspot(VirtualFile file, LiveSecurityHotspot securityHotspot) {
    securityHotspotsPerFile.computeIfAbsent(file, k -> new ArrayList<>()).add(securityHotspot);
    streamingTriggeringAlarm.schedule();
  }

  public void stopStreaming() {
    streamingTriggeringAlarm.stop();
  }

  private void triggerStreaming(AnalysisCallback analysisCallback) {
    analysisCallback.onIntermediateResult(new AnalysisIntermediateResult(new LiveFindings(issuesPerFile, securityHotspotsPerFile)));
  }

  private static class Alarm {
    private final Duration duration;
    private final Runnable endRunnable;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> scheduledFuture;

    private Alarm(Duration duration, Runnable endRunnable) {
      this.duration = duration;
      this.endRunnable = endRunnable;
    }

    public void schedule() {
      // if already scheduled, don't re-schedule
      if (scheduledFuture == null) {
        scheduledFuture = executorService.schedule(this::notifyEnd, duration.toMillis(), TimeUnit.MILLISECONDS);
      }
    }

    private void notifyEnd() {
      if (!executorService.isShutdown()) {
        scheduledFuture = null;
        endRunnable.run();
      }
    }

    public void stop() {
      cancelRunning();
      executorService.shutdownNow();
    }

    private void cancelRunning() {
      if (scheduledFuture != null) {
        scheduledFuture.cancel(false);
      }
    }
  }
}
