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
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.EnabledLanguages
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications.Companion.projectLessNotification
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarlint.intellij.util.ProgressUtils
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput

/**
 * Startup activity that checks CFamily analyzer availability on project open.
 *
 * When the analyzer is downloaded for the first time, it triggers a backend restart
 * to ensure the backend picks up the newly available analyzer.
 *
 * This runs once per IDE session to avoid redundant checks.
 */
class CFamilyAnalyzerStartupActivity : StartupActivity {

    private var hasRun = false
    private val lock = Any()

    override fun runActivity(project: Project) {
        if (ApplicationManager.getApplication().isUnitTestMode ||
            !EnabledLanguages.isClionEnabled()
        ) {
            return
        }

        // Only run once per IDE session
        synchronized(lock) {
            if (hasRun) {
                return
            }
            hasRun = true
        }

        val task = object : Task.Backgroundable(
            project,
            "Checking CFamily Analyzer",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false
                indicator.text = "Checking CFamily analyzer..."

                try {
                    val manager = getService(CFamilyAnalyzerManager::class.java)
                    val future = manager.ensureAnalyzerAvailable(indicator)

                    when (val result = ProgressUtils.waitForFuture(indicator, future)) {
                        is CFamilyAnalyzerManager.CheckResult.Downloaded -> {
                            getService(GlobalLogOutput::class.java).log(
                                "CFamily analyzer downloaded and verified, restarting backend...",
                                ClientLogOutput.Level.INFO
                            )
                            projectLessNotification(
                                null,
                                "CFamily analyzer has been downloaded and verified. Backend is restarting to enable C/C++ analysis.",
                                NotificationType.INFORMATION
                            )
                            // Trigger backend restart to pick up the new analyzer
                            getService(BackendService::class.java).restartBackendService(true)
                        }

                        is CFamilyAnalyzerManager.CheckResult.Available -> {
                            getService(GlobalLogOutput::class.java).log(
                                "CFamily analyzer is ready and signature verified",
                                ClientLogOutput.Level.DEBUG
                            )
                        }

                        is CFamilyAnalyzerManager.CheckResult.Cancelled -> {
                            getService(GlobalLogOutput::class.java).log(
                                "CFamily analyzer check cancelled by user",
                                ClientLogOutput.Level.DEBUG
                            )
                            projectLessNotification(
                                null,
                                "CFamily analyzer check was cancelled. C/C++ analysis may not be available until the analyzer is downloaded.",
                                NotificationType.INFORMATION
                            )
                        }

                        is CFamilyAnalyzerManager.CheckResult.InvalidSignature -> {
                            getService(GlobalLogOutput::class.java).log(
                                "CFamily analyzer signature invalid",
                                ClientLogOutput.Level.ERROR
                            )
                            projectLessNotification(
                                null,
                                "CFamily analyzer signature validation failed. C/C++ analysis is disabled for security reasons.",
                                NotificationType.ERROR
                            )
                        }

                        is CFamilyAnalyzerManager.CheckResult.DownloadFailed -> {
                            getService(GlobalLogOutput::class.java).log(
                                "CFamily analyzer download failed: ${result.reason}",
                                ClientLogOutput.Level.WARN
                            )
                            projectLessNotification(
                                null,
                                "Failed to download CFamily analyzer: ${result.reason}. C/C++ analysis will not be available.",
                                NotificationType.WARNING
                            )
                        }

                        is CFamilyAnalyzerManager.CheckResult.MissingAndDownloadDisabled -> {
                            getService(GlobalLogOutput::class.java).log(
                                "CFamily analyzer not available and download disabled",
                                ClientLogOutput.Level.DEBUG
                            )
                        }
                    }
                } catch (e: ProcessCanceledException) {
                    getService(GlobalLogOutput::class.java).log(
                        "CFamily analyzer check cancelled", ClientLogOutput.Level.DEBUG)

                    projectLessNotification(
                        null,
                        "CFamily analyzer check was cancelled. C/C++ analysis may not be available until the analyzer is downloaded.",
                        NotificationType.INFORMATION
                    )

                    throw e
                } catch (e: Exception) {
                    getService(GlobalLogOutput::class.java).logError("Error checking CFamily analyzer availability", e)
                }
            }
        }

        ProgressManager.getInstance().run(task)
    }
}
