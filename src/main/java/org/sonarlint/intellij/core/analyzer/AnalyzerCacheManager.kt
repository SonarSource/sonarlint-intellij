/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
package org.sonarlint.intellij.core.analyzer

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.sonarlint.intellij.SonarLintPlugin
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput

/**
 * Manages analyzer cache with LRU (Least Recently Used) cleanup policy.
 *
 * Features:
 * - Tracks last access timestamp for each analyzer version
 * - Automatically cleans up analyzers not accessed within retention period
 * - Runs cleanup asynchronously at startup (non-blocking)
 * - Handles edge cases: downloading, single version, cleanup failures
 *
 * Metadata storage:
 * - Stored in {cache-dir}/.metadata.json
 * - Contains map of analyzer filename -> last access timestamp
 */
@Service(Service.Level.APP)
class AnalyzerCacheManager {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val cleanupInProgress = AtomicBoolean(false)
    private val currentlyDownloading = ConcurrentHashMap.newKeySet<String>()

    companion object {
        private const val METADATA_FILE_NAME = ".metadata.json"
        private const val DEFAULT_RETENTION_DAYS = 60L // 2 months
        private const val ANALYZER_CACHE_DIR = "analyzer-cache"
        private const val CFAMILY_CACHE_DIR = "cfamily"
    }

    /**
     * Metadata tracking for analyzer files
     */
    data class AnalyzerMetadata(
        @SerializedName("analyzers")
        val analyzers: MutableMap<String, Long> = mutableMapOf(),
    ) {
        fun getLastAccessTime(fileName: String): Instant? {
            return analyzers[fileName]?.let { Instant.ofEpochMilli(it) }
        }

        fun updateAccessTime(fileName: String, timestamp: Instant = Instant.now()) {
            analyzers[fileName] = timestamp.toEpochMilli()
        }

        fun removeEntry(fileName: String) {
            analyzers.remove(fileName)
        }
    }

    /**
     * Update the last access timestamp for an analyzer
     */
    fun updateAnalyzerTimestamp(analyzerPath: Path) {
        try {
            val cacheDir = getCFamilyCacheDir()
            val metadata = loadMetadata(cacheDir)
            val fileName = analyzerPath.fileName.toString()

            metadata.updateAccessTime(fileName)
            saveMetadata(cacheDir, metadata)

            getService(GlobalLogOutput::class.java).log(
                "Updated access timestamp for analyzer: $fileName",
                ClientLogOutput.Level.DEBUG
            )
        } catch (e: Exception) {
            getService(GlobalLogOutput::class.java).logError(
                "Failed to update analyzer access timestamp",
                e
            )
        }
    }

    /**
     * Mark an analyzer as currently being downloaded
     */
    fun markDownloading(fileName: String) {
        currentlyDownloading.add(fileName)
    }

    /**
     * Unmark an analyzer as downloading
     */
    fun unmarkDownloading(fileName: String) {
        currentlyDownloading.remove(fileName)
    }

    /**
     * Check if an analyzer is currently being downloaded
     */
    fun isDownloading(fileName: String): Boolean {
        return currentlyDownloading.contains(fileName)
    }

    /**
     * Start asynchronous cleanup task at startup
     */
    fun startCleanupTask() {
        if (!cleanupInProgress.compareAndSet(false, true)) {
            getService(GlobalLogOutput::class.java).log(
                "Analyzer cache cleanup is already in progress",
                ClientLogOutput.Level.DEBUG
            )
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                performCleanup()
            } catch (e: Exception) {
                getService(GlobalLogOutput::class.java).logError(
                    "Analyzer cache cleanup failed",
                    e
                )
            } finally {
                cleanupInProgress.set(false)
            }
        }
    }

    /**
     * Perform the actual cleanup
     */
    private fun performCleanup() {
        val cacheDir = getCFamilyCacheDir()
        if (!Files.isDirectory(cacheDir)) {
            getService(GlobalLogOutput::class.java).log(
                "Analyzer cache directory does not exist, skipping cleanup",
                ClientLogOutput.Level.DEBUG
            )
            return
        }

        val retentionDays = getGlobalSettings().cFamilyAnalyzerRetentionDays
        val cutoffTime = Instant.now().minus(retentionDays, ChronoUnit.DAYS)

        getService(GlobalLogOutput::class.java).log(
            "Starting analyzer cache cleanup (retention: $retentionDays days)",
            ClientLogOutput.Level.INFO
        )

        val metadata = loadMetadata(cacheDir)
        val analyzerFiles = findAnalyzerFiles(cacheDir)

        if (analyzerFiles.size <= 1) {
            getService(GlobalLogOutput::class.java).log(
                "Only one or no analyzer found, skipping cleanup",
                ClientLogOutput.Level.DEBUG
            )
            return
        }

        var deletedCount = 0
        var failedCount = 0

        for (analyzerFile in analyzerFiles) {
            val fileName = analyzerFile.fileName.toString()

            // Skip if currently downloading
            if (isDownloading(fileName)) {
                getService(GlobalLogOutput::class.java).log(
                    "Skipping $fileName - currently downloading",
                    ClientLogOutput.Level.DEBUG
                )
                continue
            }

            // Check last access time
            val lastAccess = metadata.getLastAccessTime(fileName)
            if (lastAccess == null) {
                // No metadata, use file modification time
                val fileTime = Files.getLastModifiedTime(analyzerFile).toInstant()
                metadata.updateAccessTime(fileName, fileTime)

                if (fileTime.isBefore(cutoffTime)) {
                    if (deleteAnalyzer(analyzerFile, metadata)) {
                        deletedCount++
                    } else {
                        failedCount++
                    }
                }
            } else if (lastAccess.isBefore(cutoffTime)) {
                // Old analyzer, delete it
                if (deleteAnalyzer(analyzerFile, metadata)) {
                    deletedCount++
                } else {
                    failedCount++
                }
            }
        }

        // Save updated metadata
        saveMetadata(cacheDir, metadata)

        if (deletedCount > 0 || failedCount > 0) {
            getService(GlobalLogOutput::class.java).log(
                "Analyzer cache cleanup completed: $deletedCount deleted, $failedCount failed",
                ClientLogOutput.Level.INFO
            )
        } else {
            getService(GlobalLogOutput::class.java).log(
                "Analyzer cache cleanup completed: no old analyzers found",
                ClientLogOutput.Level.DEBUG
            )
        }
    }

    /**
     * Delete an analyzer and update metadata
     */
    private fun deleteAnalyzer(analyzerFile: Path, metadata: AnalyzerMetadata): Boolean {
        val fileName = analyzerFile.fileName.toString()
        return try {
            Files.deleteIfExists(analyzerFile)
            metadata.removeEntry(fileName)

            getService(GlobalLogOutput::class.java).log(
                "Deleted old analyzer: $fileName",
                ClientLogOutput.Level.INFO
            )
            true
        } catch (e: Exception) {
            getService(GlobalLogOutput::class.java).logError(
                "Failed to delete analyzer: $fileName",
                e
            )
            false
        }
    }

    /**
     * Find all analyzer files in cache directory
     */
    private fun findAnalyzerFiles(cacheDir: Path): List<Path> {
        return try {
            Files.newDirectoryStream(cacheDir, "sonar-cfamily-plugin-*.jar").use { stream ->
                stream.toList()
            }
        } catch (e: Exception) {
            getService(GlobalLogOutput::class.java).logError(
                "Failed to list analyzer files",
                e
            )
            emptyList()
        }
    }

    /**
     * Load metadata from cache directory
     */
    private fun loadMetadata(cacheDir: Path): AnalyzerMetadata {
        val metadataFile = cacheDir.resolve(METADATA_FILE_NAME)

        return try {
            if (Files.exists(metadataFile)) {
                val json = Files.readString(metadataFile)
                gson.fromJson(json, AnalyzerMetadata::class.java) ?: AnalyzerMetadata()
            } else {
                AnalyzerMetadata()
            }
        } catch (e: Exception) {
            getService(GlobalLogOutput::class.java).logError(
                "Failed to load analyzer metadata, creating new",
                e
            )
            AnalyzerMetadata()
        }
    }

    /**
     * Save metadata to cache directory
     */
    private fun saveMetadata(cacheDir: Path, metadata: AnalyzerMetadata) {
        val metadataFile = cacheDir.resolve(METADATA_FILE_NAME)
        val tempFile = cacheDir.resolve("$METADATA_FILE_NAME.tmp")

        try {
            Files.createDirectories(cacheDir)
            val json = gson.toJson(metadata)
            Files.writeString(tempFile, json)
            Files.move(tempFile, metadataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            Files.deleteIfExists(tempFile)
            getService(GlobalLogOutput::class.java).logError(
                "Failed to save analyzer metadata",
                e
            )
        }
    }

    private fun getCFamilyCacheDir(): Path {
        val plugin = getService(SonarLintPlugin::class.java)
        return plugin.path.resolve(ANALYZER_CACHE_DIR).resolve(CFAMILY_CACHE_DIR)
    }
}
