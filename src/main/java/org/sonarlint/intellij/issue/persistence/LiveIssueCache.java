/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.issue.LiveIssue;
import org.sonarlint.intellij.util.SonarLintUtils;

public class LiveIssueCache extends AbstractProjectComponent {
  private static final Logger LOGGER = Logger.getInstance(LiveIssueCache.class);
  static final int MAX_ENTRIES = 100;
  private final Map<VirtualFile, Collection<LiveIssue>> cache;
  private final IssuePersistence store;

  public LiveIssueCache(Project project, IssuePersistence store) {
    super(project);
    this.store = store;
    this.cache = new LimitedSizeLinkedHashMap();
  }

  /**
   * Keeps a maximum number of entries in the map. On insertion, if the limit is passed, the entry accessed the longest time ago
   * is flushed into cache and removed from the map.
   */
  private class LimitedSizeLinkedHashMap extends LinkedHashMap<VirtualFile, Collection<LiveIssue>> {
    LimitedSizeLinkedHashMap() {
      super(MAX_ENTRIES, 0.75f, true);
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<VirtualFile, Collection<LiveIssue>> eldest) {
      if (size() <= MAX_ENTRIES) {
        return false;
      }

      if(eldest.getKey().isValid()) {
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
      if(virtualFile.isValid()) {
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
  public synchronized void projectClosed() {
    flushAll();
  }

  public synchronized void clear() {
    store.clear();
    cache.clear();
  }

  public synchronized boolean contains(VirtualFile virtualFile) {
    return getLive(virtualFile) != null;
  }

  private String createKey(VirtualFile virtualFile) {
    return SonarLintUtils.getRelativePath(myProject, virtualFile);
  }
}
