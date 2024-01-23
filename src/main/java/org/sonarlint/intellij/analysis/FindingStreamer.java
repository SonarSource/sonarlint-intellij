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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.vfs.VirtualFile;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.sonarlint.intellij.finding.LiveFindings;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.util.Alarm;

public class FindingStreamer implements AutoCloseable {
  public static final Duration STREAMING_INTERVAL = Duration.ofMillis(300);
  private final Alarm streamingTriggeringAlarm;
  private final Map<VirtualFile, Collection<LiveIssue>> issuesPerFile = new ConcurrentHashMap<>();
  private final Map<VirtualFile, Collection<LiveSecurityHotspot>> securityHotspotsPerFile = new ConcurrentHashMap<>();

  public FindingStreamer(AnalysisCallback analysisCallback) {
    this.streamingTriggeringAlarm = new Alarm("sonarlint-finding-streamer", STREAMING_INTERVAL, () -> this.triggerStreaming(analysisCallback));
  }

  public void streamIssue(VirtualFile file, LiveIssue issue) {
    issuesPerFile.computeIfAbsent(file, k -> new ArrayList<>()).add(issue);
    streamingTriggeringAlarm.schedule();
  }

  public void streamSecurityHotspot(VirtualFile file, LiveSecurityHotspot securityHotspot) {
    securityHotspotsPerFile.computeIfAbsent(file, k -> new ArrayList<>()).add(securityHotspot);
    streamingTriggeringAlarm.schedule();
  }

  private void triggerStreaming(AnalysisCallback analysisCallback) {
    analysisCallback.onIntermediateResult(new AnalysisIntermediateResult(new LiveFindings(issuesPerFile, securityHotspotsPerFile)));
  }

  @Override
  public void close() {
    stopStreaming();
  }

  private void stopStreaming() {
    streamingTriggeringAlarm.shutdown();
  }

}
