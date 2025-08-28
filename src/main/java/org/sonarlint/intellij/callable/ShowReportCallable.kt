/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
package org.sonarlint.intellij.callable

import com.intellij.openapi.project.Project
import org.sonarlint.intellij.analysis.AnalysisCallback
import org.sonarlint.intellij.analysis.AnalysisResult
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.editor.CodeAnalyzerRestarter
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.report.ReportTabManager

class ShowReportCallable(private val project: Project) : AnalysisCallback {

    private val batchId: String = generateBatchId()

    override fun onSuccess(analysisResult: AnalysisResult) {
        // All UI operations must run on EDT, with synchronization happening on EDT
        runOnUiThread(project) {
            synchronized(this@ShowReportCallable) {
                val reportTabManager = SonarLintUtils.getService(project, ReportTabManager::class.java)
                reportTabManager.updateOrCreateReportTab(batchId, analysisResult)
                SonarLintUtils.getService(project, CodeAnalyzerRestarter::class.java).refreshOpenFiles()
            }
        }
    }

    override fun onError(e: Throwable) {
        // For errors, we don't need to do anything special - just let the existing tab remain
    }
    
    companion object {
        private fun generateBatchId(): String {
            return "batch-${System.currentTimeMillis()}"
        }
    }

}
