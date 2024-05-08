/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
package org.sonarlint.intellij.core

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import java.nio.file.Paths
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.core.BackendService.Companion.moduleId
import org.sonarlint.intellij.util.AnalysisLogOutput
import org.sonarsource.sonarlint.core.analysis.api.AnalysisResults
import org.sonarsource.sonarlint.core.analysis.api.ClientInputFile
import org.sonarsource.sonarlint.core.client.legacy.analysis.AnalysisConfiguration
import org.sonarsource.sonarlint.core.client.legacy.analysis.RawIssueListener
import org.sonarsource.sonarlint.core.client.legacy.analysis.SonarLintAnalysisEngine
import org.sonarsource.sonarlint.core.commons.api.progress.ClientProgressMonitor

class EngineFacade(private val project: Project, private val engine: SonarLintAnalysisEngine) {
    fun startAnalysis(module: Module, inputFiles: Collection<ClientInputFile>, props: Map<String, String>, issueListener: RawIssueListener, progressMonitor: ClientProgressMonitor): AnalysisResults {
        val baseDir = Paths.get(project.basePath)
        val extraProperties = props + Settings.getSettingsFor(project).additionalProperties
        val config = AnalysisConfiguration.builder().setBaseDir(baseDir).addInputFiles(inputFiles).putAllExtraProperties(extraProperties).setModuleKey(module).build()
        val console: SonarLintConsole = getService(project, SonarLintConsole::class.java)
        console.debug("Starting analysis with configuration:\n$config")

        val analysisResults: AnalysisResults = engine.analyze(config, issueListener, AnalysisLogOutput(project), progressMonitor, moduleId(module))
        return analysisResults
    }
}
