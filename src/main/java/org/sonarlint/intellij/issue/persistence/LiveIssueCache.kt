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
package org.sonarlint.intellij.issue.persistence

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.issue.LiveIssue
import org.sonarlint.intellij.messages.IssueStoreListener
import org.sonarlint.intellij.util.SonarLintAppUtils
import org.sonarlint.intellij.util.SonarLintUtils
import java.io.IOException


class LiveIssueCache internal constructor(private val myProject: Project, private val maxEntries: Int) {
  private val cache: LinkedHashMap<VirtualFile, MutableCollection<LiveIssue>>
  private var snapshot = CurrentFileAnalysisCache(null, 0)
  private var currentAnalysis = CurrentFileAnalysisCache(null, 0)

  constructor(project: Project) : this(project, DEFAULT_MAX_ENTRIES)

  @Synchronized
  fun analysisStarted() {
    currentAnalysis = CurrentFileAnalysisCache(snapshot.file, snapshot.snapshotVersion + 1)
    currentAnalysis.issues.addAll(snapshot.issues)
  }

  @Synchronized
  fun analysisFinished() {
    snapshot = currentAnalysis
  }

  /**
   * Keeps a maximum number of entries in the map. On insertion, if the limit is passed, the entry accessed the longest time ago
   * is flushed into cache and removed from the map.
   */
  private inner class LimitedSizeLinkedHashMap : LinkedHashMap<VirtualFile, MutableCollection<LiveIssue>>(
    maxEntries, 0.75f, true
  ) {
    override fun removeEldestEntry(eldest: Map.Entry<VirtualFile, MutableCollection<LiveIssue>>): Boolean {
      if (size <= maxEntries) {
        return false
      }
      if (eldest.key.isValid) {
        val key = createKey(eldest.key)
        if (key != null) {
          try {
            LOGGER.debug("Persisting issues for $key")
            val store = SonarLintUtils.getService(myProject, IssuePersistence::class.java)
            store.save(key, eldest.value)
          } catch (e: IOException) {
            throw IllegalStateException(String.format("Error persisting issues for %s", key), e)
          }
        }
      }
      return true
    }
  }

  /**
   * Read issues from a file that are cached. On cache miss, it won't fallback to the persistent store.
   */
  @Synchronized
  fun getLive(virtualFile: VirtualFile): Collection<LiveIssue>? {
    return if (!currentAnalysis.isClean && virtualFile == currentAnalysis.file) {
      currentAnalysis.issues
    } else cache[virtualFile]
  }

  @Synchronized
  fun save(virtualFile: VirtualFile, issues: Collection<LiveIssue>?) {
    currentAnalysis.update(virtualFile, issues)
    cache[virtualFile] = ArrayList(issues)
    println("break")
  }

  @Synchronized
  fun save(virtualFile: VirtualFile, issue: LiveIssue) {
    currentAnalysis.update(virtualFile, issue, cache[virtualFile] ?: emptyList())
    cache.computeIfAbsent(virtualFile) { ArrayList() }.add(issue)
    if (newIssue(virtualFile, issue)) {
      myProject.messageBus.syncPublisher(IssueStoreListener.SONARLINT_ISSUE_STORE_TOPIC)
        .fileChanged(virtualFile, listOf(issue))
    }
  }

  private fun newIssue(virtualFile: VirtualFile, issue: LiveIssue): Boolean {
    snapshot.file ?: return true
    if (virtualFile == snapshot.file && snapshot.issues.contains(issue)) {
      return true
    }
    return false
  }

  /**
   * Flushes all cached entries to disk.
   * It does not clear the cache.
   */
  @Synchronized
  fun flushAll() {
    LOGGER.debug("Persisting all issues")
    cache.forEach { (virtualFile: VirtualFile, trackableIssues: Collection<LiveIssue>?) ->
      if (virtualFile.isValid) {
        val key = createKey(virtualFile)
        if (key != null) {
          try {
            val store = SonarLintUtils.getService(myProject, IssuePersistence::class.java)
            store.save(key, trackableIssues)
          } catch (e: IOException) {
            throw IllegalStateException("Failed to flush cache", e)
          }
        }
      }
    }
  }

  @Synchronized
  fun clearCurrent() {
    snapshot.clear()
    currentAnalysis.clear()
  }

  /**
   * Clear cache and underlying persistent store
   */
  @Synchronized
  fun clear() {
    val store = SonarLintUtils.getService(myProject, IssuePersistence::class.java)
    store.clear()
    cache.clear()
  }

  @Synchronized
  fun clear(virtualFile: VirtualFile) {
    val key = createKey(virtualFile)
    if (key != null) {
      cache.remove(virtualFile)
      if (!currentAnalysis.isClean && currentAnalysis.file != null && virtualFile == currentAnalysis.file) {
        currentAnalysis.clear()
        snapshot.clear()
      }
      try {
        val store = SonarLintUtils.getService(myProject, IssuePersistence::class.java)
        store.clear(key)
      } catch (e: IOException) {
        throw IllegalStateException("Failed to clear cache", e)
      }
    }
  }

  @Synchronized
  operator fun contains(virtualFile: VirtualFile): Boolean {
    return getLive(virtualFile) != null
  }

  private fun createKey(virtualFile: VirtualFile): String? {
    return SonarLintAppUtils.getRelativePathForAnalysis(myProject, virtualFile)
  }

  companion object {
    private val LOGGER = Logger.getInstance(
      LiveIssueCache::class.java
    )
    const val DEFAULT_MAX_ENTRIES = 10000
  }

  init {
    cache = LimitedSizeLinkedHashMap()
  }
}

internal class CurrentFileAnalysisCache(var file: VirtualFile?, var snapshotVersion: Long) {
  val issues: MutableCollection<LiveIssue> = ArrayList()
  val isClean: Boolean
    get() = file == null && issues.isEmpty()


  fun clear() {
    issues.clear()
    file = null
    snapshotVersion = 0
  }

  fun update(virtualFile: VirtualFile, issue: LiveIssue, prevIssues: Collection<LiveIssue>) {
    if (!isClean && virtualFile != file) {
      clear()
    }
    issues.addAll(prevIssues)
    issues.add(issue)
    file = virtualFile
  }

  fun update(virtualFile: VirtualFile, issues: Collection<LiveIssue>?) {
    if (!isClean && virtualFile != file) {
      clear()
    }
    this.issues.addAll(issues!!)
    file = virtualFile
  }

  companion object {
    private val LOGGER = Logger.getInstance(CurrentFileAnalysisCache::class.java)
  }
}
