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
package org.sonarlint.intellij.issue.persistence;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.jetbrains.annotations.NotNull;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.util.SonarLintAppUtils;

public class LiveIssueCache implements ProjectManagerListener, Disposable {
  private static final Logger LOGGER = Logger.getInstance(LiveIssueCache.class);
  static final int DEFAULT_MAX_ENTRIES = 10_000;
  private final Map<VirtualFile, Collection<LiveIssue>> cache;
  private final Project project;
  private final ProjectManager projectManager;
  private final IssuePersistence store;
  private final SonarLintAppUtils appUtils;
  private final int maxEntries;

  public LiveIssueCache(Project project, ProjectManager projectManager, IssuePersistence store, SonarLintAppUtils appUtils) {
    this(project, projectManager, store, appUtils, DEFAULT_MAX_ENTRIES);
  }

  LiveIssueCache(Project project, ProjectManager projectManager, IssuePersistence store, SonarLintAppUtils appUtils, int maxEntries) {
    this.project = project;
    this.projectManager = projectManager;
    this.store = store;
    this.appUtils = appUtils;
    this.maxEntries = maxEntries;
    this.cache = new LimitedSizeLinkedHashMap();
    projectManager.addProjectManagerListener(this.project, this);
  }

  @Override
  public void dispose() {
    projectManager.removeProjectManagerListener(this.project, this);
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
        try {
          LOGGER.debug("Persisting issues for " + key);
          store.save(key, eldest.getValue());
        } catch (IOException e) {
          throw new IllegalStateException(String.format("Error persisting issues for %s", key), e);
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
    return cache.get(virtualFile);
  }

  public synchronized void save(VirtualFile virtualFile, Collection<LiveIssue> issues) {
    cache.put(virtualFile, Collections.unmodifiableCollection(issues));
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
        try {
          store.save(key, trackableIssues);
        } catch (IOException e) {
          throw new IllegalStateException("Failed to flush cache", e);
        }
      }
    });
  }

  @Override
  public void projectClosing(@NotNull Project project) {
    // Flush issues before project is closed, because we need to resolve module paths to compute the key
    flushAll();
  }

  /**
   * Clear cache and underlying persistent store
   */
  public synchronized void clear() {
    store.clear();
    cache.clear();
  }

  public synchronized void clear(VirtualFile virtualFile) {
    String key = createKey(virtualFile);
    if (key != null) {
      cache.remove(virtualFile);
      try {
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
    return appUtils.getRelativePathForAnalysis(this.project, virtualFile);
  }
}
