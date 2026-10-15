/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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

class ShowReportCallable(private val project: Project, private val expectedModuleCount: Int = 1) : AnalysisCallback {

    // Accessed only on EDT. A closed report keeps its empty handle, preventing recreation.
    private var report: ReportTabManager.ReportTab? = null
    private var completedModuleCount = 0

    init {
        runOnUiThread(project) { createReportIfNeeded() }
    }

    private fun createReportIfNeeded() {
        if (report == null) {
            report = SonarLintUtils.getService(project, ReportTabManager::class.java).createLoadingReportTab(expectedModuleCount)
        }
    }

    override fun onSuccess(analysisResult: AnalysisResult) {
        runOnUiThread(project) {
            createReportIfNeeded()
            completedModuleCount++
            report?.update(analysisResult, completedModuleCount, expectedModuleCount)
            SonarLintUtils.getService(project, CodeAnalyzerRestarter::class.java).refreshOpenFiles()
        }
    }

    override fun onError(e: Throwable) {
        // Leave the report open so successful modules remain available.
    }
}
