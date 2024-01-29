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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.config.Settings;
import org.sonarlint.intellij.finding.RawIssueAdapter;
import org.sonarlint.intellij.finding.TextRangeMatcher;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.finding.persistence.CachedFindings;
import org.sonarlint.intellij.finding.tracking.LocalHistoryFindingTracker;
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications;
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.commons.Language;
import org.sonarsource.sonarlint.core.commons.RuleType;

class RawFindingHandler implements IssueListener {
  private Module module;
  private final ConcurrentHashMap<VirtualFile, Collection<LiveIssue>> issuesPerFile = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<VirtualFile, Collection<LiveSecurityHotspot>> securityHotspotsPerFile = new ConcurrentHashMap<>();
  private final AtomicInteger rawIssueCounter = new AtomicInteger();
  private final FindingStreamer findingStreamer;
  private final LocalHistoryFindingTracker localHistoryFindingTracker;

  public RawFindingHandler(FindingStreamer findingStreamer, CachedFindings previousFindings) {
    this.localHistoryFindingTracker = new LocalHistoryFindingTracker(previousFindings);
    this.findingStreamer = findingStreamer;
  }

  public void setCurrentModule(Module module) {
    this.module = module;
  }

  @Override
  public void handle(Issue rawIssue) {
    rawIssueCounter.incrementAndGet();

    // Do issue tracking for the single issue
    var inputFile = rawIssue.getInputFile();
    if (inputFile == null || inputFile.getPath() == null) {
      // ignore project level issues
      return;
    }
    VirtualFile file = inputFile.getClientObject();
    if (!file.isValid()) {
      // file is no longer valid (might have been deleted meanwhile) or there has been an error matching an issue in it
      return;
    }
    if (RuleType.SECURITY_HOTSPOT.equals(rawIssue.getType())) {
      processSecurityHotspot(inputFile, rawIssue);
    } else {
      processIssue(inputFile, rawIssue);
    }
  }

  private void processSecurityHotspot(ClientInputFile inputFile, Issue rawIssue) {
    LiveSecurityHotspot liveSecurityHotspot;
    VirtualFile file = inputFile.getClientObject();
    try {
      liveSecurityHotspot = RawIssueAdapter.toLiveSecurityHotspot(module, rawIssue, inputFile);
      if (liveSecurityHotspot == null) {
        return;
      }
    } catch (TextRangeMatcher.NoMatchException e) {
      // File content is likely to have changed during the analysis, should be fixed in next analysis
      SonarLintConsole.get(module.getProject()).debug("Failed to find location of Security Hotspot for file: '" + file.getName() + "'." + e.getMessage());
      return;
    } catch (ProcessCanceledException e) {
      throw e;
    } catch (Exception e) {
      SonarLintConsole.get(module.getProject()).error("Error finding location for Security Hotspot", e);
      return;
    }

    localHistoryFindingTracker.matchWithPreviousSecurityHotspot(file, liveSecurityHotspot);
    securityHotspotsPerFile.computeIfAbsent(file, f -> new ArrayList<>()).add(liveSecurityHotspot);
    findingStreamer.streamSecurityHotspot(file, liveSecurityHotspot);
  }

  private void processIssue(ClientInputFile inputFile, Issue rawIssue) {
    LiveIssue liveIssue;
    VirtualFile file = inputFile.getClientObject();
    try {
      liveIssue = RawIssueAdapter.toLiveIssue(module, rawIssue, inputFile);
      if (liveIssue == null) {
        return;
      }
    } catch (TextRangeMatcher.NoMatchException e) {
      // File content is likely to have changed during the analysis, should be fixed in next analysis
      SonarLintConsole.get(module.getProject()).debug("Failed to find location of issue for file: '" + file.getName() + "'." + e.getMessage());
      return;
    } catch (ProcessCanceledException e) {
      throw e;
    } catch (Exception e) {
      SonarLintConsole.get(module.getProject()).error("Error finding location for issue", e);
      return;
    }

    localHistoryFindingTracker.matchWithPreviousIssue(file, liveIssue);
    issuesPerFile.computeIfAbsent(file, f -> new ArrayList<>()).add(liveIssue);
    findingStreamer.streamIssue(file, liveIssue);

    var sonarLintGlobalSettings = Settings.getGlobalSettings();
    if (sonarLintGlobalSettings.isSecretsNeverBeenAnalysed() && liveIssue.getRuleKey().contains(Language.SECRETS.getPluginKey())) {
      SonarLintProjectNotifications.Companion.get(module.getProject()).sendNotification();
      sonarLintGlobalSettings.rememberNotificationOnSecretsBeenSent();
    }
  }

  public int getRawIssueCount() {
    return rawIssueCounter.get();
  }

  public Map<VirtualFile, Collection<LiveIssue>> getIssuesPerFile() {
    return issuesPerFile;
  }

  public Map<VirtualFile, Collection<LiveSecurityHotspot>> getSecurityHotspotsPerFile() {
    return securityHotspotsPerFile;
  }
}
