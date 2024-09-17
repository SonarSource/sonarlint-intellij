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
package org.sonarlint.intellij.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.sonarlint.intellij.analysis.AnalysisStatus
import org.sonarlint.intellij.analysis.AnalysisSubmitter
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.config.project.ExclusionItem
import org.sonarlint.intellij.messages.ProjectConfigurationListener
import org.sonarlint.intellij.trigger.TriggerType
import org.sonarlint.intellij.util.SonarLintAppUtils
import org.sonarlint.intellij.util.runOnPooledThread

class ExcludeFileAction : AbstractSonarAction {
    constructor() : super()
    constructor(text: String) : super(text, null, AllIcons.Actions.Cancel)

    override fun isVisible(e: AnActionEvent): Boolean {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        return ActionPlaces.isPopupPlace(e.place) && !files.isNullOrEmpty() && !isRiderSlnOrCsproj(files)
    }

    override fun isEnabled(e: AnActionEvent, project: Project, status: AnalysisStatus): Boolean {
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.asSequence() ?: return false
        val exclusions = Settings.getSettingsFor(project).fileExclusions.toSet()
        return toExclusionPatterns(project, files).any { !exclusions.contains(it) }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        if (project.isDisposed || files.isEmpty()) {
            return
        }
        runOnPooledThread(project) {
            val settings = Settings.getSettingsFor(project)
            val exclusions = ArrayList(settings.fileExclusions)
            val newExclusions = toExclusionPatterns(project, files.asSequence()).toList()
            if (newExclusions.isNotEmpty()) {
                exclusions.addAll(newExclusions)
                settings.fileExclusions = exclusions
                getService(project, AnalysisSubmitter::class.java).autoAnalyzeOpenFiles(TriggerType.CONFIG_CHANGE)
                project.messageBus.syncPublisher(ProjectConfigurationListener.TOPIC).changed(settings)
            }
        }
    }

    private fun toExclusionPatterns(project: Project, files: Sequence<VirtualFile>): Sequence<String> {
        return files.mapNotNull { toExclusion(project, it) }
                .filter { it.item().isNotEmpty() }
                .map { it.toStringWithType() }
    }

    private fun toExclusion(project: Project, virtualFile: VirtualFile): ExclusionItem? {
        val relativeFilePath = SonarLintAppUtils.getRelativePathForAnalysis(project, virtualFile) ?: return null
        return if (virtualFile.isDirectory) {
            ExclusionItem(ExclusionItem.Type.DIRECTORY, relativeFilePath)
        } else {
            ExclusionItem(ExclusionItem.Type.FILE, relativeFilePath)
        }
    }
}
