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
package org.sonarlint.intellij.issue.persistence;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.util.*;
import javax.annotation.CheckForNull;

import org.apache.commons.collections.collection.UnmodifiableCollection;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.util.SonarLintAppUtils;
import org.sonarlint.intellij.util.SonarLintUtils;

public class LiveIssueCache {
  private static final Logger LOGGER = Logger.getInstance(LiveIssueCache.class);
  static final int DEFAULT_MAX_ENTRIES = 10_000;
  private final Map<VirtualFile, Collection<LiveIssue>> cache;
  private final Project myproject;
  private final int maxEntries;
  private Collection<LiveIssue> snapshot;
  private Collection<LiveIssue> currentAnalysis = new ArrayList<>();
  private VirtualFile currentFile;
  private long snapshotVersion = 0;

  public LiveIssueCache(Project project) {
    this(project, DEFAULT_MAX_ENTRIES);
  }

  LiveIssueCache(Project project, int maxEntries) {
    this.myproject = project;
    this.maxEntries = maxEntries;
    this.cache = new LimitedSizeLinkedHashMap();
  }

  public synchronized void add(LiveIssue issue) {
    currentAnalysis.add(issue);
  }

  public synchronized void analysisStarted() {
    // TODO may be this method is redundant
  }

  public synchronized void analysisFinished() {
    snapshotVersion++;
    snapshot = Collections.unmodifiableCollection(currentAnalysis);

  }

  /**
   * Keeps a maximum number of entries in the map. On insertion, if the limit is passed, the entry accessed the longest time ago
   * is flushed into cache and removed from the map.
   */
  private class LimitedSizeLinkedHashMap extends LinkedHashMap<VirtualFile, Collection<LiveIssue>> {
    LimitedSizeLinkedHashMap() {
      super(maxEntries, 0.75f, true);
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<VirtualFile, Collection<LiveIssue>> eldest) {
      if (size() <= maxEntries) {
        return false;
      }

      if (eldest.getKey().isValid()) {
        String key = createKey(eldest.getKey());
        if (key != null) {
          try {
            LOGGER.debug("Persisting issues for " + key);
            IssuePersistence store = SonarLintUtils.getService(myproject, IssuePersistence.class);
            store.save(key, eldest.getValue());
          } catch (IOException e) {
            throw new IllegalStateException(String.format("Error persisting issues for %s", key), e);
          }
        }
      }
      return true;
    }
  }

  /**
   * Read issues from a file that are cached. On cache miss, it won't fallback to the persistent store.
   */
  @CheckForNull
  public synchronized Collection<LiveIssue> getLive(VirtualFile virtualFile) {
    if(currentFile != null && currentFile.equals(virtualFile)) {
      return snapshot;
    }
    return cache.get(virtualFile);
  }

  public synchronized void save(VirtualFile virtualFile, Collection<LiveIssue> issues) {
    issues.forEach(it -> updateCurrent(virtualFile, it));
    cache.put(virtualFile, new ArrayList<>(issues));
  }

  public synchronized void save(VirtualFile virtualFile, LiveIssue issue) {
    updateCurrent(virtualFile, issue);
    if (!cache.containsKey(virtualFile)) {
      cache.put(virtualFile, new ArrayList<>());
    }
    cache.get(virtualFile).add(issue);
  }

  private void updateCurrent(VirtualFile virtualFile, LiveIssue issue) {
    if (!virtualFile.equals(currentFile)) {
      currentAnalysis.clear();
    }
    currentAnalysis.add(issue);
    currentFile = virtualFile;
  }

  /**
   * Flushes all cached entries to disk.
   * It does not clear the cache.
   */
  public synchronized void flushAll() {
    LOGGER.debug("Persisting all issues");
    cache.forEach((virtualFile, trackableIssues) -> {
      if (virtualFile.isValid()) {
        String key = createKey(virtualFile);
        if (key != null) {
          try {
            IssuePersistence store = SonarLintUtils.getService(myproject, IssuePersistence.class);
            store.save(key, trackableIssues);
          } catch (IOException e) {
            throw new IllegalStateException("Failed to flush cache", e);
          }
        }
      }
    });
  }

  /**
   * Clear cache and underlying persistent store
   */
  public synchronized void clear() {
    IssuePersistence store = SonarLintUtils.getService(myproject, IssuePersistence.class);
    store.clear();
    snapshot = Collections.emptyList();
    currentFile = null;
    currentAnalysis = new ArrayList<>();
    cache.clear();
  }

  public synchronized void clear(VirtualFile virtualFile) {
    String key = createKey(virtualFile);
    if (key != null) {
      cache.remove(virtualFile);
      try {
        IssuePersistence store = SonarLintUtils.getService(myproject, IssuePersistence.class);
        store.clear(key);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to clear cache", e);
      }
    }
  }

  public synchronized boolean contains(VirtualFile virtualFile) {
    return getLive(virtualFile) != null;
  }

  private String createKey(VirtualFile virtualFile) {
    return SonarLintAppUtils.getRelativePathForAnalysis(this.myproject, virtualFile);
  }
}
