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
package org.sonarlint.intellij.core.cfamily

import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.io.HttpRequests
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.analyzer.AnalyzerCacheManager
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput

/**
 * Service responsible for downloading CFamily analyzer JARs.
 * Downloads are stored in {plugin.path}/analyzer-cache/cfamily/
 */
@Service(Service.Level.APP)
class CFamilyAnalyzerDownloader {

    companion object {
        private const val CFAMILY_DOWNLOAD_URL_TEMPLATE =
            "https://binaries.sonarsource.com/CommercialDistribution/sonar-cfamily-plugin/sonar-cfamily-plugin-%s.jar"
    }

    sealed class DownloadResult {
        data class Success(val path: Path) : DownloadResult()
        data class Failed(val reason: String) : DownloadResult()
    }

    fun downloadAnalyzer(version: String, progressIndicator: ProgressIndicator): DownloadResult {
        progressIndicator.text = "Preparing to download CFamily analyzer..."
        progressIndicator.isIndeterminate = false

        getService(GlobalLogOutput::class.java).log(
            "Downloading CFamily analyzer version: $version to cache",
            ClientLogOutput.Level.INFO
        )

        val cacheManager = getService(AnalyzerCacheManager::class.java)
        val targetFileName = "sonar-cfamily-plugin-$version.jar"

        try {
            val cacheDir = cacheManager.getCFamilyCacheDir()
            Files.createDirectories(cacheDir)

            // Mark as downloading to prevent cleanup
            cacheManager.markDownloading(targetFileName)

            val downloadUrl = String.format(CFAMILY_DOWNLOAD_URL_TEMPLATE, version)
            val targetFile = cacheDir.resolve(targetFileName)
            val tempFile = Files.createTempFile(cacheDir, "cfamily-download-", ".jar")

            try {
                progressIndicator.text = "Downloading CFamily analyzer ($version)"

                HttpRequests.request(downloadUrl)
                    .connectTimeout(30000)
                    .readTimeout(30000)
                    .saveToFile(tempFile.toFile(), progressIndicator)

                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING)

                // Update access timestamp for newly downloaded analyzer
                cacheManager.updateAnalyzerTimestamp(targetFile)
                cacheManager.unmarkDownloading(targetFileName)

                getService(GlobalLogOutput::class.java).log(
                    "CFamily analyzer downloaded successfully to cache: ${targetFile.fileName}",
                    ClientLogOutput.Level.INFO
                )

                return DownloadResult.Success(targetFile)
            } catch (e: ProcessCanceledException) {
                Files.deleteIfExists(tempFile)
                cacheManager.unmarkDownloading(targetFileName)
                getService(GlobalLogOutput::class.java).log(
                    "CFamily analyzer download cancelled by user",
                    ClientLogOutput.Level.INFO
                )
                throw e
            } catch (e: Exception) {
                Files.deleteIfExists(tempFile)
                cacheManager.unmarkDownloading(targetFileName)
                getService(GlobalLogOutput::class.java).logError("Error downloading CFamily analyzer", e)
                return DownloadResult.Failed(e.message ?: "Unknown error")
            }
        } catch (e: ProcessCanceledException) {
            // User cancelled during setup
            cacheManager.unmarkDownloading(targetFileName)
            getService(GlobalLogOutput::class.java).log(
                "CFamily analyzer download cancelled during setup",
                ClientLogOutput.Level.INFO
            )
            throw e
        } catch (e: Exception) {
            cacheManager.unmarkDownloading(targetFileName)
            getService(GlobalLogOutput::class.java).logError("Error preparing download", e)
            return DownloadResult.Failed(e.message ?: "Unknown error")
        }
    }

}
