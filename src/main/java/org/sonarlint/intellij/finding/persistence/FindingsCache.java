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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.serviceContainer.NonInjectable;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.concurrent.ThreadSafe;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.finding.hotspot.LiveSecurityHotspot;
import org.sonarlint.intellij.finding.issue.LiveIssue;
import org.sonarlint.intellij.finding.tracking.Trackable;

import static java.util.Collections.emptyList;

/**
 * Stores findings associated to a {@link PsiElement}, {@link RangeMarker} or  {@link PsiFile}.
 * Findings are either issues or hotspots.
 * Findings can then be displayed as annotations at any time.
 */
@ThreadSafe
public class FindingsCache {
  private final Project myProject;
  private final LiveFindingCache<LiveIssue> liveIssueCache;
  private final LiveFindingCache<LiveSecurityHotspot> liveSecurityHotspotCache;

  public FindingsCache(Project project) {
    this(project, new LiveFindingCache<>(project, new FindingPersistence<>(project, "issue")));
  }

  @NonInjectable
  FindingsCache(Project project, LiveFindingCache<LiveIssue> liveIssueCache) {
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
  }

  public void clearAllFindingsForAllFiles() {
    liveIssueCache.clear();
    liveSecurityHotspotCache.clear();
  }

  private void clearAllFindingsForFiles(Collection<VirtualFile> files) {
    files.forEach(liveIssueCache::clear);
    files.forEach(liveSecurityHotspotCache::clear);
  }

  public Collection<LiveIssue> getIssuesForFile(VirtualFile file) {
    var issues = liveIssueCache.getLive(file);
    return issues != null ? issues : emptyList();
  }

  public Collection<LiveSecurityHotspot> getSecurityHotspotsForFile(VirtualFile file) {
    var hotspots = liveSecurityHotspotCache.getLive(file);
    return hotspots != null ? hotspots : emptyList();
  }

  public CachedFindings clearFindings(Collection<VirtualFile> files) {
    var previousIssuesPerFile = getPreviousIssuesByFile(files);
    var previousSecurityHotspotsPerFile = getPreviousSecurityHotspotsByFile(files);
    var findingsSnapshot = new CachedFindings(previousIssuesPerFile, previousSecurityHotspotsPerFile);
    ReadAction.run(() -> clearAllFindingsForFiles(files));
    return findingsSnapshot;
  }

  private Map<VirtualFile, Collection<Trackable>> getPreviousIssuesByFile(Collection<VirtualFile> files) {
    return files.stream().collect(Collectors.toMap(Function.identity(), this::getPreviousIssues));
  }

  private Map<VirtualFile, Collection<Trackable>> getPreviousSecurityHotspotsByFile(Collection<VirtualFile> files) {
    return files.stream().collect(Collectors.toMap(Function.identity(), this::getPreviousSecurityHotspots));
  }

  private Collection<Trackable> getPreviousIssues(VirtualFile file) {
    return liveIssueCache.getPreviousFindings(file);
  }

  private Collection<Trackable> getPreviousSecurityHotspots(VirtualFile file) {
    return liveSecurityHotspotCache.getPreviousFindings(file);
  }

  public void insertNewIssue(VirtualFile file, LiveIssue liveIssue) {
    liveIssueCache.insertFinding(file, liveIssue);
  }

  public void insertNewSecurityHotspot(VirtualFile file, LiveSecurityHotspot liveSecurityHotspot) {
    liveSecurityHotspotCache.insertFinding(file, liveSecurityHotspot);
  }
}
