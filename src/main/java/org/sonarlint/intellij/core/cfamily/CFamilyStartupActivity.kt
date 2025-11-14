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

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput

/**
 * Startup activity that ensures CFamily analyzer is available before the backend starts.
 * This runs once per IDE session on the first project open.
 */
class CFamilyStartupActivity : ProjectActivity {
    
    companion object {
        private var hasRun = false
        private val lock = Any()
    }

    override suspend fun execute(project: Project) {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            return
        }

        // Only run once per IDE session
        synchronized(lock) {
            if (hasRun) {
                return
            }
            hasRun = true
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "SonarQube: Checking CFamily Analyzer",
            true,
            ALWAYS_BACKGROUND
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.text = "Checking CFamily analyzer..."
                
                try {
                    val manager = getService(CFamilyAnalyzerManager::class.java)
                    val result = manager.ensureAnalyzerAvailable(indicator).get()
                    handleCheckResult(result, project)
                } catch (e: Exception) {
                    getService(GlobalLogOutput::class.java).logError(
                        "Error checking CFamily analyzer availability",
                        e
                    )
                }
            }
            
            override fun onCancel() {
                getService(GlobalLogOutput::class.java).log(
                    "CFamily analyzer check was cancelled by user",
                    ClientLogOutput.Level.INFO
                )
            }
        })
    }

    private fun handleCheckResult(result: CFamilyAnalyzerManager.CheckResult, project: Project) {
        when (result) {
            is CFamilyAnalyzerManager.CheckResult.Available -> {
                getService(GlobalLogOutput::class.java).log(
                    "CFamily analyzer is available and signature verified",
                    ClientLogOutput.Level.INFO
                )
            }
            
            is CFamilyAnalyzerManager.CheckResult.Downloaded -> {
                getService(GlobalLogOutput::class.java).log(
                    "CFamily analyzer was downloaded and signature verified",
                    ClientLogOutput.Level.INFO
                )
                
                // Notify user that restart is required
                SonarLintProjectNotifications.projectLessNotification(
                    null,
                    "CFamily analyzer has been downloaded and verified",
                    NotificationType.INFORMATION
                )
            }
            
            is CFamilyAnalyzerManager.CheckResult.InvalidSignature -> {
                getService(GlobalLogOutput::class.java).log(
                    "CFamily analyzer signature validation failed. The analyzer will not be used for security reasons.",
                    ClientLogOutput.Level.ERROR
                )
                
                SonarLintProjectNotifications.projectLessNotification(
                    null,
                    "CFamily analyzer signature validation failed. C/C++ analysis is disabled for security reasons.",
                    NotificationType.ERROR
                )
            }
            
            is CFamilyAnalyzerManager.CheckResult.DownloadFailed -> {
                getService(GlobalLogOutput::class.java).log(
                    "Failed to download CFamily analyzer: ${result.reason}",
                    ClientLogOutput.Level.ERROR
                )
                
                SonarLintProjectNotifications.projectLessNotification(
                    null,
                    "Failed to download CFamily analyzer: ${result.reason}. C/C++ analysis will not be available.",
                    NotificationType.WARNING
                )
            }
            
            is CFamilyAnalyzerManager.CheckResult.MissingAndDownloadDisabled -> {
                getService(GlobalLogOutput::class.java).log(
                    "CFamily analyzer is not available and download is disabled by user preference",
                    ClientLogOutput.Level.INFO
                )
                
                SonarLintProjectNotifications.projectLessNotification(
                    null,
                    "CFamily analyzer not available. C/C++ analysis is disabled. " +
                    "To enable, uncheck 'Never download CFamily analyzer' in Settings > Tools > SonarQube > General.",
                    NotificationType.INFORMATION
                )
            }
            
            is CFamilyAnalyzerManager.CheckResult.Cancelled -> {
                getService(GlobalLogOutput::class.java).log(
                    "CFamily analyzer download was cancelled",
                    ClientLogOutput.Level.INFO
                )
            }
        }
    }
}
