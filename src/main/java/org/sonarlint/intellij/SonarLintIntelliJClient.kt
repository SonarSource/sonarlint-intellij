/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2022 SonarSource
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
package org.sonarlint.intellij

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.orNull
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.config.Settings.getSettingsFor
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.notifications.binding.BindingSuggestion
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient
import org.sonarsource.sonarlint.core.clientapi.config.binding.BindingSuggestionDto
import org.sonarsource.sonarlint.core.clientapi.config.binding.SuggestBindingParams
import org.sonarsource.sonarlint.core.clientapi.fs.FindFileByNamesInScopeParams
import org.sonarsource.sonarlint.core.clientapi.fs.FindFileByNamesInScopeResponse
import org.sonarsource.sonarlint.core.clientapi.fs.FoundFileDto
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput
import java.util.concurrent.CompletableFuture

class SonarLintIntelliJClient : SonarLintClient {
    override fun suggestBinding(params: SuggestBindingParams) {
        params.suggestions.forEach { (projectId, suggestions) -> suggestAutoBind(findProject(projectId), suggestions) }
    }

    private fun suggestAutoBind(project: Project?, suggestions: List<BindingSuggestionDto>) {
        if (project == null) {
            GlobalLogOutput.get().log("Discarding binding suggestions, project was closed", ClientLogOutput.Level.DEBUG)
            return
        }
        if (getSettingsFor(project).isBindingSuggestionsEnabled) {
            val notifications = getService(project, SonarLintProjectNotifications::class.java)
            notifications.suggestBindingOptions(suggestions.map {
                BindingSuggestion(
                    it.connectionId, it.sonarProjectKey, it.sonarProjectName
                )
            })
        }
    }

    override fun findFileByNamesInScope(params: FindFileByNamesInScopeParams): CompletableFuture<FindFileByNamesInScopeResponse> {
        val project = findProject(params.configScopeId) ?: return CompletableFuture.completedFuture(
            FindFileByNamesInScopeResponse(emptyList())
        )

        val foundFiles = ReadAction.compute<List<FoundFileDto>, Exception> {
            findFiles(project, params)
        }

        return CompletableFuture.completedFuture(FindFileByNamesInScopeResponse(foundFiles))
    }

    private fun findFiles(project: Project, params: FindFileByNamesInScopeParams): List<FoundFileDto> {
        if (project.isDisposed) {
            return emptyList()
        }
        val fileNames = params.filenames
        val foundVirtualFiles = findInContentRoots(project, fileNames) + findInProjectBaseDir(project, fileNames)
        return foundVirtualFiles.map {
            FoundFileDto(
                it.name, it.path, getFileContent(it)
            )
        }
    }

    private fun findInContentRoots(
        project: Project, fileNames: List<String>
    ): Set<VirtualFile> {
        val contentRoots = ProjectRootManager.getInstance(project).contentRoots.filter { it.isDirectory }
        return fileNames.mapNotNull { fileName ->
            contentRoots.firstNotNullOfOrNull { root ->
                root.findFileByRelativePath(
                    fileName
                )
            }
        }.toSet()
    }

    // useful for Rider where the files to find are not located in content roots
    private fun findInProjectBaseDir(project: Project, fileNames: List<String>): Set<VirtualFile> {
        val projectDir = project.guessProjectDir() ?: return emptySet()
        return fileNames.mapNotNull { fileName ->
            projectDir.findFileByRelativePath(
                fileName
            )
        }.toSet()
    }

    private fun findProject(configScopeId: String): Project? {
        return ProjectManager.getInstance().openProjects.find { configScopeId == BackendService.projectId(it) }
    }

    private fun getFileContent(virtualFile: VirtualFile): String {
        val fileDocumentManager = FileDocumentManager.getInstance()
        if (fileDocumentManager.isFileModified(virtualFile)) {
            val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            if (document != null) {
                return document.text
            }
        }
        return virtualFile.contentsToByteArray().toString(virtualFile.charset)
    }

    override fun getHttpClient(connectionId: String) =
        getGlobalSettings().getServerConnectionByName(connectionId).map { it.httpClient }.orNull()

}
