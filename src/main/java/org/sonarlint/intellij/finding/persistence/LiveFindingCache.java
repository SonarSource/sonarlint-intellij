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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.sonarlint.intellij.common.ui.SonarLintConsole;
import org.sonarlint.intellij.finding.LiveFinding;
import org.sonarlint.intellij.finding.tracking.Trackable;
import org.sonarlint.intellij.util.SonarLintAppUtils;

import static java.util.Collections.emptyList;
import static org.sonarlint.intellij.common.ui.ReadActionUtils.computeReadActionSafely;

public class LiveFindingCache<T extends LiveFinding> {
  static final int DEFAULT_MAX_ENTRIES = 10_000;
  private final Map<VirtualFile, Collection<T>> cache;
  private final FindingPersistence<T> persistence;
  private final Project project;
  private final int maxEntries;

  public LiveFindingCache(Project project, FindingPersistence<T> persistence) {
    this(project, persistence, DEFAULT_MAX_ENTRIES);
  }

  LiveFindingCache(Project project, FindingPersistence<T> persistence, int maxEntries) {
    this.project = project;
    this.persistence = persistence;
    this.maxEntries = maxEntries;
    this.cache = new LimitedSizeLinkedHashMap();
  }

  public void replaceFindings(Map<VirtualFile, Collection<T>> newFindingsPerFile) {
    cache.putAll(newFindingsPerFile);
  }

  /**
   * Keeps a maximum number of entries in the map. On insertion, if the limit is passed, the entry accessed the longest time ago
   * is flushed into cache and removed from the map.
   */
  private class LimitedSizeLinkedHashMap extends LinkedHashMap<VirtualFile, Collection<T>> {
    LimitedSizeLinkedHashMap() {
      super(maxEntries, 0.75f, true);
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<VirtualFile, Collection<T>> eldest) {
      if (size() <= maxEntries) {
        return false;
      }

      if (eldest.getKey().isValid()) {
        var key = createKey(eldest.getKey());
        if (key != null) {
          try {
            SonarLintConsole.get(project).debug("Persisting findings for " + key);
            persistence.save(key, eldest.getValue());
          } catch (IOException e) {
            throw new IllegalStateException(String.format("Error persisting findings for %s", key), e);
          }
        }
      }
      return true;
    }
  }

  public boolean wasEverAnalyzed(VirtualFile file) {
    if (contains(file)) {
      return true;
    }
    var storeKey = SonarLintAppUtils.getRelativePathForAnalysis(project, file);
    if (storeKey == null) {
      return false;
    }
    return persistence.contains(storeKey);
  }

  public Collection<Trackable> getPreviousFindings(VirtualFile file) {
    var liveFindings = getLive(file);
    if (liveFindings != null) {
      Collection<Trackable> result = computeReadActionSafely(project, () -> liveFindings.stream().filter(T::isValid).collect(Collectors.toList()));
      return result == null ? emptyList() : result;
    }

    var storeKey = SonarLintAppUtils.getRelativePathForAnalysis(project, file);
    if (storeKey == null) {
      return emptyList();
    }
    var storeFindings = persistence.read(storeKey);
    return storeFindings != null ? new ArrayList<>(storeFindings) : emptyList();
  }

  /**
   * Read findings from a file that are cached. On cache miss, it won't fallback to the persistent store.
   */
  @CheckForNull
  public synchronized Collection<T> getLive(VirtualFile virtualFile) {
    var liveFindings = cache.get(virtualFile);
    if (liveFindings != null) {
      // Create a copy to avoid concurrent modification issues
      return new ArrayList<>(liveFindings);
    }
    return null;
  }

  public synchronized void insertFinding(VirtualFile virtualFile, T finding) {
    cache.computeIfAbsent(virtualFile, f -> new ArrayList<>()).add(finding);
  }

  public synchronized void removeFindings(VirtualFile virtualFile, Collection<T> findings) {
    cache.computeIfAbsent(virtualFile, f -> new ArrayList<>()).removeAll(findings);
  }

  /**
   * Flushes all cached entries to disk.
   * It does not clear the cache.
   */
  public synchronized void flushAll() {
    SonarLintConsole.get(project).debug("Persisting all findings");
    cache.forEach((virtualFile, liveFindings) -> {
      if (virtualFile.isValid()) {
        var key = createKey(virtualFile);
        if (key != null) {
          try {
            persistence.save(key, liveFindings);
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
    persistence.clear();
    cache.clear();
  }

  public synchronized void clear(VirtualFile virtualFile) {
    var key = createKey(virtualFile);
    if (key != null) {
      cache.remove(virtualFile);
      try {
        persistence.clear(key);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to clear cache", e);
      }
    }
  }

  public synchronized boolean contains(VirtualFile virtualFile) {
    return getLive(virtualFile) != null;
  }

  private String createKey(VirtualFile virtualFile) {
    return SonarLintAppUtils.getRelativePathForAnalysis(this.project, virtualFile);
  }
}
