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
import org.sonarlint.intellij.util.SonarLintAppUtils
import org.sonarlint.intellij.util.SonarLintUtils
import java.io.IOException
import java.util.*

open class LiveIssueCache internal constructor(private val myProject: Project, private val maxEntries: Int) {

    private val cache: MutableMap<VirtualFile, Collection<LiveIssue>> = LimitedSizeLinkedHashMap()
    constructor(project: Project) : this(project, DEFAULT_MAX_ENTRIES)

    /**
     * Keeps a maximum number of entries in the map. On insertion, if the limit is passed, the entry accessed the longest time ago
     * is flushed into cache and removed from the map.
     */
    private inner class LimitedSizeLinkedHashMap : LinkedHashMap<VirtualFile, Collection<LiveIssue>>(
        maxEntries, 0.75f, true
    ) {

        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<VirtualFile, Collection<LiveIssue>>): Boolean {
            if (size <= maxEntries) {
                return false
            }
            if (eldest.key.isValid) {
                val key = createKey(eldest.key)
                if (key != null) {
                    try {
                        LOGGER.debug("Persisting issues for $key")
                        val store = SonarLintUtils.getService(
                            myProject, IssuePersistence::class.java
                        )
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
    open fun getLive(virtualFile: VirtualFile): Collection<LiveIssue>? {
        return cache[virtualFile]
    }

    @Synchronized
    open fun save(virtualFile: VirtualFile, issues: Collection<LiveIssue>) {
        cache[virtualFile] = issues
    }

    /**
     * Flushes all cached entries to disk.
     * It does not clear the cache.
     */
    @Synchronized
    open fun flushAll() {
        LOGGER.debug("Persisting all issues")
        cache.forEach { (virtualFile: VirtualFile, trackableIssues: Collection<LiveIssue>?) ->
            if (virtualFile.isValid) {
                val key = createKey(virtualFile)
                if (key != null) {
                    try {
                        val store = SonarLintUtils.getService(
                            myProject, IssuePersistence::class.java
                        )
                        store.save(key, trackableIssues)
                    } catch (e: IOException) {
                        throw IllegalStateException("Failed to flush cache", e)
                    }
                }
            }
        }
    }

    /**
     * Clear cache and underlying persistent store
     */
    @Synchronized
    open fun clear() {
        val store = SonarLintUtils.getService(myProject, IssuePersistence::class.java)
        store.clear()
        cache.clear()
    }

    @Synchronized
    open fun clear(virtualFile: VirtualFile) {
        val key = createKey(virtualFile)
        if (key != null) {
            cache.remove(virtualFile)
            try {
                val store = SonarLintUtils.getService(
                    myProject, IssuePersistence::class.java
                )
                store.clear(key)
            } catch (e: IOException) {
                throw IllegalStateException("Failed to clear cache", e)
            }
        }
    }

    @Synchronized
    open operator fun contains(virtualFile: VirtualFile): Boolean {
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

}
