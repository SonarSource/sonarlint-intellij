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
import com.intellij.openapi.vfs.VirtualFileManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.finding.RawIssueAdapter;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.finding.persistence.CachedFindings;
import org.sonarlint.intellij.finding.tracking.LocalHistoryFindingTracker;
import org.sonarsource.sonarlint.core.commons.log.SonarLintLogger;
import org.sonarsource.sonarlint.core.rpc.protocol.client.analysis.RawIssueDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.RuleType;

public class AnalysisState {

  private final UUID id;
  private final Module module;
  private final ConcurrentHashMap<VirtualFile, Collection<LiveIssue>> issuesPerFile = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<VirtualFile, Collection<LiveSecurityHotspot>> securityHotspotsPerFile = new ConcurrentHashMap<>();
  private final AtomicInteger rawIssueCounter = new AtomicInteger();
  private final FindingStreamer findingStreamer;
  private final LocalHistoryFindingTracker localHistoryFindingTracker;
  private final ConcurrentLinkedQueue<RawIssueDto> currentRawIssuesReceived = new ConcurrentLinkedQueue<>();

  public AnalysisState(UUID analysisId, FindingStreamer findingStreamer, CachedFindings previousFindings, Collection<VirtualFile> filesToAnalyze, Module module) {
    this.id = analysisId;
    this.localHistoryFindingTracker = new LocalHistoryFindingTracker(previousFindings);
    this.findingStreamer = findingStreamer;
    this.initFiles(filesToAnalyze);
    this.module = module;
  }

  public void initFiles(Collection<VirtualFile> files) {
    files.forEach(file -> {
      issuesPerFile.computeIfAbsent(file, f -> new ArrayList<>());
      securityHotspotsPerFile.computeIfAbsent(file, f -> new ArrayList<>());
    });
  }

  public UUID getId() {
    return id;
  }

  public boolean wasIssueNotAlreadyReceived(RawIssueDto rawIssue) {
    return currentRawIssuesReceived.stream().noneMatch(i -> areSameRawIssues(i, rawIssue));
  }

  public void addRawStreamingIssue(RawIssueDto rawIssue) {
    currentRawIssuesReceived.add(rawIssue);
    addRawIssue(rawIssue);
  }

  public void addRawIssue(RawIssueDto rawIssue) {
    rawIssueCounter.incrementAndGet();

    // Do issue tracking for the single issue
    var fileUri = rawIssue.getFileUri();
    if (fileUri == null) {
      // ignore project level issues
      return;
    }

    var virtualFile = VirtualFileManager.getInstance().findFileByUrl(fileUri.toString());
    if (virtualFile == null) {
      SonarLintLogger.get().error("Cannot retrieve the file on which an issue has been raised. File URI is " + fileUri);
      return;
    }

    if (!virtualFile.isValid()) {
      // file is no longer valid (might have been deleted meanwhile) or there has been an error matching an issue in it
      return;
    }

    if (RuleType.SECURITY_HOTSPOT.equals(rawIssue.getType())) {
      processSecurityHotspot(virtualFile, rawIssue);
    } else {
      processIssue(virtualFile, rawIssue);
    }
  }

  private void processSecurityHotspot(VirtualFile virtualFile, RawIssueDto rawIssue) {
    LiveSecurityHotspot liveSecurityHotspot;
    try {
      liveSecurityHotspot = RawIssueAdapter.toLiveSecurityHotspot(module, rawIssue, virtualFile);
      if (liveSecurityHotspot == null) {
        return;
      }
    } catch (ProcessCanceledException e) {
      throw e;
    } catch (Exception e) {
      SonarLintConsole.get(module.getProject()).error("Error finding location for Security Hotspot", e);
      return;
    }

    localHistoryFindingTracker.matchWithPreviousSecurityHotspot(virtualFile, liveSecurityHotspot);
    securityHotspotsPerFile.computeIfAbsent(virtualFile, f -> new ArrayList<>()).add(liveSecurityHotspot);
    findingStreamer.streamSecurityHotspot(virtualFile, liveSecurityHotspot);
  }

  private void processIssue(VirtualFile virtualFile, RawIssueDto rawIssue) {
    LiveIssue liveIssue;
    try {
      liveIssue = RawIssueAdapter.toLiveIssue(module, rawIssue, virtualFile);
      if (liveIssue == null) {
        return;
      }
    } catch (ProcessCanceledException e) {
      throw e;
    } catch (Exception e) {
      SonarLintConsole.get(module.getProject()).error("Error finding location for issue", e);
      return;
    }

    localHistoryFindingTracker.matchWithPreviousIssue(virtualFile, liveIssue);
    issuesPerFile.computeIfAbsent(virtualFile, f -> new ArrayList<>()).add(liveIssue);
    findingStreamer.streamIssue(virtualFile, liveIssue);
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

  private static boolean areSameRawIssues(RawIssueDto rawIssue1, RawIssueDto rawIssue2) {
    var areSame = rawIssue1.getCleanCodeAttribute().name().equals(rawIssue2.getCleanCodeAttribute().name())
      && rawIssue1.getPrimaryMessage().equals(rawIssue2.getPrimaryMessage())
      && Objects.equals(rawIssue1.getFileUri(), rawIssue2.getFileUri())
      && Objects.equals(rawIssue1.getRuleDescriptionContextKey(), (rawIssue2.getRuleDescriptionContextKey()))
      && rawIssue1.getRuleKey().equals(rawIssue2.getRuleKey())
      && rawIssue1.getSeverity().name().equals(rawIssue2.getSeverity().name())
      && rawIssue1.getType().name().equals(rawIssue2.getType().name());
    var textRange1 = rawIssue1.getTextRange();
    var textRange2 = rawIssue2.getTextRange();
    if (textRange1 != null && textRange2 != null) {
      areSame = areSame && textRange1.getStartLine() == textRange2.getStartLine()
        && textRange1.getEndLine() == textRange2.getEndLine()
        && textRange1.getStartLineOffset() == textRange2.getStartLineOffset()
        && textRange1.getEndLineOffset() == textRange2.getEndLineOffset();
    } else {
      areSame = areSame && textRange1 == textRange2;
    }
    var vulnProb1 = rawIssue1.getVulnerabilityProbability();
    var vulnProb2 = rawIssue2.getVulnerabilityProbability();
    if (vulnProb1 != null && vulnProb2 != null) {
      areSame = vulnProb1.name().equals(vulnProb2.name());
    } else {
      areSame = areSame && vulnProb1 == vulnProb2;
    }
    return areSame;
  }

}
