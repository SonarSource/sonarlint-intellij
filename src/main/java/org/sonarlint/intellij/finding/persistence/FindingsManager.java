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
package org.sonarlint.intellij.finding.persistence;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.serviceContainer.NonInjectable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.concurrent.ThreadSafe;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.finding.LiveFinding;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.finding.tracking.Input;
import org.sonarlint.intellij.finding.tracking.Trackable;
import org.sonarlint.intellij.finding.tracking.Tracker;
import org.sonarlint.intellij.finding.tracking.Tracking;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.messages.FindingStoreListener;
import org.sonarsource.sonarlint.core.commons.IssueSeverity;

import static java.util.Collections.emptyList;

/**
 * Stores findings associated to a {@link PsiElement}, {@link RangeMarker} or  {@link PsiFile}.
 * Findings are either issues or hotspots.
 * Findings can then be displayed as annotations at any time.
 */
@ThreadSafe
public class FindingsManager {
  private final Project myProject;
  private final LiveFindingCache<LiveIssue> liveIssueCache;
  private final LiveFindingCache<LiveSecurityHotspot> liveSecurityHotspotCache;

  public FindingsManager(Project project) {
    this(project, new LiveFindingCache<>(project, new FindingPersistence<>(project, "issue")));
  }

  @NonInjectable
  FindingsManager(Project project, LiveFindingCache<LiveIssue> liveIssueCache) {
    myProject = project;
    this.liveIssueCache = liveIssueCache;
    this.liveSecurityHotspotCache = new LiveFindingCache<>(project, new FindingPersistence<>(project, "securityhotspot"));
    project.getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        try {
          if (project == myProject) {
            // Flush findings before project is closed, because we need to resolve module paths to compute the key
            liveIssueCache.flushAll();
            liveSecurityHotspotCache.flushAll();
          }
        } catch (Exception e) {
          SonarLintConsole.get(myProject).error("Cannot flush issues", e);
        }
      }
    });
  }

  public void clearAllIssuesForAllFiles() {
    liveIssueCache.clear();
    myProject.getMessageBus().syncPublisher(FindingStoreListener.SONARLINT_ISSUE_STORE_TOPIC).allChanged();
  }

  public void clearAllFindingsForAllFiles() {
    liveIssueCache.clear();
    liveSecurityHotspotCache.clear();
    myProject.getMessageBus().syncPublisher(FindingStoreListener.SONARLINT_ISSUE_STORE_TOPIC).allChanged();
  }

  public void clearAllFindingsForFiles(Collection<VirtualFile> files) {
    files.forEach(liveIssueCache::clear);
    files.forEach(liveSecurityHotspotCache::clear);
    myProject.getMessageBus().syncPublisher(FindingStoreListener.SONARLINT_ISSUE_STORE_TOPIC).filesChanged(new HashSet<>(files));
  }

  public void removeResolvedFindings(VirtualFile file, List<LiveIssue> issues, List<LiveSecurityHotspot> securityHotspots) {
    liveIssueCache.removeFindings(file, issues);
    liveSecurityHotspotCache.removeFindings(file, securityHotspots);
    notifyFindingsChangedForFile(file);
  }

  public boolean neverAnalyzedSinceStartup(VirtualFile file) {
    return liveIssueCache.getLive(file) == null && liveSecurityHotspotCache.getLive(file) == null;
  }

  public Collection<LiveIssue> getIssuesForFile(VirtualFile file) {
    var issues = liveIssueCache.getLive(file);
    return issues != null ? issues : emptyList();
  }

  public Collection<LiveSecurityHotspot> getSecurityHotspotsForFile(VirtualFile file) {
    var hotspots = liveSecurityHotspotCache.getLive(file);
    return hotspots != null ? hotspots : emptyList();
  }

  public Map<VirtualFile, Collection<Trackable>> getPreviousIssuesByFile(List<VirtualFile> files) {
    return files.stream().collect(Collectors.toMap(Function.identity(), this::getPreviousIssues));
  }

  public Map<VirtualFile, Collection<Trackable>> getPreviousSecurityHotspotsByFile(List<VirtualFile> files) {
    return files.stream().collect(Collectors.toMap(Function.identity(), this::getPreviousSecurityHotspots));
  }

  public Collection<Trackable> getPreviousIssues(VirtualFile file) {
    return liveIssueCache.getPreviousFindings(file);
  }

  public Collection<Trackable> getPreviousSecurityHotspots(VirtualFile file) {
    return liveSecurityHotspotCache.getPreviousFindings(file);
  }

  public boolean wasEverAnalyzed(VirtualFile file) {
    return liveIssueCache.wasEverAnalyzed(file) || liveSecurityHotspotCache.wasEverAnalyzed(file);
  }

  public void matchWithServerIssues(VirtualFile file, final Collection<Trackable> serverIssues) {
    matchWithServerFindings(file, serverIssues, getIssuesForFile(file));
  }

  public void matchWithServerSecurityHotspots(VirtualFile file, final Collection<Trackable> serverSecurityHotspots) {
    matchWithServerFindings(file, serverSecurityHotspots, getSecurityHotspotsForFile(file));
  }

  private <T extends LiveFinding> void matchWithServerFindings(VirtualFile file, final Collection<Trackable> serverSecurityHotspots,
    Collection<T> previousFindings) {
    Input<Trackable> baseInput = () -> serverSecurityHotspots;
    Input<T> rawInput = () -> previousFindings;

    trackServerFinding(baseInput, rawInput);
    notifyFindingsChangedForFile(file);
  }

  private static <T extends Trackable, L extends LiveFinding> void trackServerFinding(Input<T> baseInput, Input<L> rawInput) {
    var tracking = new Tracker<L, T>().track(rawInput, baseInput);
    for (var entry : tracking.getMatchedRaws().entrySet()) {
      var liveMatched = entry.getKey();
      var serverMatched = entry.getValue();
      copyAttributesFromServer(liveMatched, serverMatched);
    }
    for (var liveUnmatched : tracking.getUnmatchedRaws()) {
      if (liveUnmatched.getServerFindingKey() != null) {
        // were matched before with server security hotspots, now not anymore
        wipeServerFindingDetails(liveUnmatched);
      } else if (liveUnmatched.getCreationDate() == null) {
        // first time seen, even locally
        liveUnmatched.setCreationDate(System.currentTimeMillis());
      }
    }
  }

  public <T extends Trackable> LiveIssue trackSingleIssue(VirtualFile file, Collection<T> baseInput, LiveIssue rawInput) {
    return trackSingleFinding(file, baseInput, rawInput, liveIssueCache);
  }

  public <T extends Trackable> LiveSecurityHotspot trackSingleSecurityHotspot(VirtualFile file, Collection<T> baseInput, LiveSecurityHotspot rawInput) {
    return trackSingleFinding(file, baseInput, rawInput, liveSecurityHotspotCache);
  }

  public <T extends Trackable, L extends LiveFinding> L trackSingleFinding(VirtualFile file, Collection<T> baseInput,
    L rawInput, LiveFindingCache<L> cache) {
    Tracking<L, T> tracking = new Tracker<L, T>().track(() -> List.of(rawInput), () -> baseInput);
    if (!tracking.getMatchedRaws().isEmpty()) {
      T previousMatched = tracking.getMatchedRaws().get(rawInput);
      baseInput.remove(previousMatched);
      copyFromPrevious(rawInput, previousMatched);
    } else {
      // first time seen, even locally
      rawInput.setCreationDate(System.currentTimeMillis());
    }
    cache.insertFinding(file, rawInput);
    notifyFindingsChangedForFile(file);
    // Should never be reachable
    return rawInput;
  }

  /**
   * Previous matched will be either server issue or preexisting local issue.
   */
  private static <L extends LiveFinding> void copyFromPrevious(L rawMatched, Trackable previousMatched) {
    rawMatched.setCreationDate(previousMatched.getCreationDate());
    // FIXME should we not reset those fields when unbinding a project?
    rawMatched.setServerFindingKey(previousMatched.getServerFindingKey());
    rawMatched.setResolved(previousMatched.isResolved());
  }

  private static <L extends LiveFinding> void copyAttributesFromServer(L liveFinding, Trackable serverIssue) {
    liveFinding.setCreationDate(serverIssue.getCreationDate());
    liveFinding.setServerFindingKey(serverIssue.getServerFindingKey());
    liveFinding.setResolved(serverIssue.isResolved());
    IssueSeverity userSeverity = serverIssue.getUserSeverity();
    if (userSeverity != null) {
      liveFinding.setSeverity(userSeverity);
    }
    if (liveFinding instanceof LiveIssue) {
      ((LiveIssue) liveFinding).setType(serverIssue.getType());
    }
  }

  private static <L extends LiveFinding> void wipeServerFindingDetails(L finding) {
    // we keep creation date from the old server issue
    finding.setServerFindingKey(null);
    finding.setResolved(false);
  }

  public void insertNewIssue(VirtualFile file, LiveIssue liveIssue) {
    liveIssueCache.insertFinding(file, liveIssue);
    notifyFindingsChangedForFile(file);
  }

  public void insertNewSecurityHotspot(VirtualFile file, LiveSecurityHotspot liveSecurityHotspot) {
    liveSecurityHotspotCache.insertFinding(file, liveSecurityHotspot);
    notifyFindingsChangedForFile(file);
  }

  private void notifyFindingsChangedForFile(VirtualFile file) {
    myProject.getMessageBus().syncPublisher(FindingStoreListener.SONARLINT_ISSUE_STORE_TOPIC).fileChanged(file);
  }

}
