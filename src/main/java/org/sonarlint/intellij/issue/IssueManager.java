/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
package org.sonarlint.intellij.issue;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.serviceContainer.NonInjectable;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.concurrent.ThreadSafe;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.issue.persistence.IssuePersistence;
import org.sonarlint.intellij.issue.persistence.LiveIssueCache;
import org.sonarlint.intellij.issue.tracking.Input;
import org.sonarlint.intellij.issue.tracking.Trackable;
import org.sonarlint.intellij.issue.tracking.Tracker;
import org.sonarlint.intellij.issue.tracking.Tracking;
import org.sonarlint.intellij.messages.IssueStoreListener;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarlint.intellij.util.SonarLintUtils;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * Stores issues associated to a {@link PsiElement}, {@link RangeMarker} or  {@link PsiFile}.
 * Issues can then be displayed as annotations at any time.
 */
@ThreadSafe
public class IssueManager {
  private static final Logger LOGGER = Logger.getInstance(IssueManager.class);
  private final Project myProject;
  private final LiveIssueCache liveIssueCache;

  public IssueManager(Project project) {
    this(project, new LiveIssueCache(project));
  }

  @NonInjectable
  IssueManager(Project project, LiveIssueCache liveIssueCache) {
    myProject = project;
    this.liveIssueCache = liveIssueCache;
    project.getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectClosing(@NotNull Project project) {
        try {
          if (project == myProject) {
            // Flush issues before project is closed, because we need to resolve module paths to compute the key
            liveIssueCache.flushAll();
          }
        } catch (Exception e) {
          LOGGER.error("Cannot flush issues", e);
        }
      }
    });
  }

  public void clearAllIssuesForAllFiles() {
    liveIssueCache.clear();
    myProject.getMessageBus().syncPublisher(IssueStoreListener.SONARLINT_ISSUE_STORE_TOPIC).allChanged();
  }

  public void clearAllIssuesForFiles(Collection<VirtualFile> files) {
    files.stream().forEach(liveIssueCache::clear);
    myProject.getMessageBus().syncPublisher(IssueStoreListener.SONARLINT_ISSUE_STORE_TOPIC).filesChanged(new HashSet<>(files));
  }

  public void removeResolvedIssues(VirtualFile file, List<LiveIssue> issues) {
    liveIssueCache.removeIssues(file, issues);
    notifyAboutIssueChangeForFile(file);
  }

  /**
   * Returns the issues in the live cache for a file.
   * If the file was never analyzed, null is returned. If the file was analyzed but no issues were found, an empty Collection is returned.
   */
  @CheckForNull
  public Collection<LiveIssue> getForFileOrNull(VirtualFile file) {
    return liveIssueCache.getLive(file);
  }

  public Collection<LiveIssue> getForFile(VirtualFile file) {
    Collection<LiveIssue> issues = liveIssueCache.getLive(file);
    return issues != null ? issues : emptyList();
  }

  public Collection<Trackable> getPreviousIssues(VirtualFile file) {
    Collection<LiveIssue> liveIssues = liveIssueCache.getLive(file);
    if (liveIssues != null) {
      return ReadAction.compute(() -> liveIssues.stream().filter(LiveIssue::isValid).collect(Collectors.toList()));
    }

    String storeKey = SonarLintAppUtils.getRelativePathForAnalysis(myProject, file);
    if (storeKey == null) {
      return emptyList();
    }
    try {
      IssuePersistence store = SonarLintUtils.getService(myProject, IssuePersistence.class);
      Collection<LocalIssueTrackable> storeIssues = store.read(storeKey);
      return storeIssues != null ? Collections.unmodifiableCollection(storeIssues) : emptyList();
    } catch (IOException e) {
      LOGGER.error(String.format("Failed to read issues from store for file %s", file.getPath()), e);
      return emptyList();
    }
  }

  public boolean wasAnalyzed(VirtualFile file) {
    if (liveIssueCache.contains(file)) {
      return true;
    }
    String storeKey = SonarLintAppUtils.getRelativePathForAnalysis(myProject, file);
    if (storeKey == null) {
      return false;
    }
    IssuePersistence store = SonarLintUtils.getService(myProject, IssuePersistence.class);
    return store.contains(storeKey);
  }

  public void matchWithServerIssues(VirtualFile file, final Collection<Trackable> serverIssues) {
    Collection<LiveIssue> previousIssues = getForFile(file);
    Input<Trackable> baseInput = () -> serverIssues;
    Input<LiveIssue> rawInput = () -> previousIssues;

    trackServerIssues(baseInput, rawInput);
    notifyAboutIssueChangeForFile(file);
  }

  private static <T extends Trackable> void trackServerIssues(Input<T> baseInput, Input<LiveIssue> rawInput) {
    Tracking<LiveIssue, T> tracking = new Tracker<LiveIssue, T>().track(rawInput, baseInput);
    for (Map.Entry<LiveIssue, ? extends Trackable> entry : tracking.getMatchedRaws().entrySet()) {
      LiveIssue liveMatched = entry.getKey();
      Trackable serverMatched = entry.getValue();
      copyAttributesFromServer(liveMatched, serverMatched);
    }
    for (LiveIssue liveUnmatched : tracking.getUnmatchedRaws()) {
      if (liveUnmatched.getServerIssueKey() != null) {
        // were matched before with server issues, now not anymore
        wipeServerIssueDetails(liveUnmatched);
      } else if (liveUnmatched.getCreationDate() == null) {
        // first time seen, even locally
        liveUnmatched.setCreationDate(System.currentTimeMillis());
      }
    }
  }

  public <T extends Trackable> LiveIssue trackSingleIssue(VirtualFile file, Collection<T> baseInput, LiveIssue rawInput) {
    Tracking<LiveIssue, T> tracking = new Tracker<LiveIssue, T>().track(() -> singletonList(rawInput), () -> baseInput);
    if (!tracking.getMatchedRaws().isEmpty()) {
      T previousMatched = tracking.getMatchedRaws().get(rawInput);
      baseInput.remove(previousMatched);
      copyFromPrevious(rawInput, previousMatched);
      liveIssueCache.insertIssue(file, rawInput);
      notifyAboutIssueChangeForFile(file);
    } else {
      // first time seen, even locally
      rawInput.setCreationDate(System.currentTimeMillis());
      liveIssueCache.insertIssue(file, rawInput);
      notifyAboutIssueChangeForFile(file);
    }
    // Should never be reachable
    return rawInput;
  }

  /**
   * Previous matched will be either server issue or preexisting local issue.
   */
  private static void copyFromPrevious(LiveIssue rawMatched, Trackable previousMatched) {
    rawMatched.setCreationDate(previousMatched.getCreationDate());
    // FIXME should we not reset those fields when unbinding a project?
    rawMatched.setServerIssueKey(previousMatched.getServerIssueKey());
    rawMatched.setResolved(previousMatched.isResolved());
    rawMatched.setAssignee(previousMatched.getAssignee());
  }

  private static void copyAttributesFromServer(LiveIssue liveIssue, Trackable serverIssue) {
    liveIssue.setCreationDate(serverIssue.getCreationDate());
    liveIssue.setServerIssueKey(serverIssue.getServerIssueKey());
    liveIssue.setResolved(serverIssue.isResolved());
    liveIssue.setAssignee(serverIssue.getAssignee());
    liveIssue.setSeverity(serverIssue.getSeverity());
    if (serverIssue.getType() != null) {
      // old SQ servers won't return this field
      liveIssue.setType(serverIssue.getType());
    }
  }

  private static void wipeServerIssueDetails(LiveIssue issue) {
    // we keep creation date from the old server issue
    issue.setServerIssueKey(null);
    issue.setResolved(false);
    issue.setAssignee("");
  }

  public void insertNewIssue(VirtualFile file, LiveIssue liveIssue) {
    liveIssueCache.insertIssue(file, liveIssue);
    notifyAboutIssueChangeForFile(file);
  }

  private void notifyAboutIssueChangeForFile(VirtualFile file) {
    myProject.getMessageBus().syncPublisher(IssueStoreListener.SONARLINT_ISSUE_STORE_TOPIC).fileChanged(file);
  }

}
