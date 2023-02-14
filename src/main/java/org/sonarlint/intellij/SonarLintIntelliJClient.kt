/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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

import com.intellij.ide.BrowserUtil
import com.intellij.ide.impl.ProjectUtil
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.containers.orNull
import org.apache.commons.lang.StringEscapeUtils
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.analysis.AnalysisSubmitter
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.config.Settings.getSettingsFor
import org.sonarlint.intellij.config.global.wizard.ServerConnectionCreator
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.http.ApacheHttpClient.Companion.default
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.notifications.binding.BindingSuggestion
import org.sonarlint.intellij.ui.ProjectSelectionDialog
import org.sonarlint.intellij.ui.SonarLintToolWindowFactory
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarlint.intellij.util.computeInEDT
import org.sonarsource.sonarlint.core.clientapi.SonarLintClient
import org.sonarsource.sonarlint.core.clientapi.backend.config.binding.BindingSuggestionDto
import org.sonarsource.sonarlint.core.clientapi.client.OpenUrlInBrowserParams
import org.sonarsource.sonarlint.core.clientapi.client.SuggestBindingParams
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingParams
import org.sonarsource.sonarlint.core.clientapi.client.binding.AssistBindingResponse
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionParams
import org.sonarsource.sonarlint.core.clientapi.client.connection.AssistCreatingConnectionResponse
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeParams
import org.sonarsource.sonarlint.core.clientapi.client.fs.FindFileByNamesInScopeResponse
import org.sonarsource.sonarlint.core.clientapi.client.fs.FoundFileDto
import org.sonarsource.sonarlint.core.clientapi.client.host.GetHostInfoResponse
import org.sonarsource.sonarlint.core.clientapi.client.hotspot.ShowHotspotParams
import org.sonarsource.sonarlint.core.clientapi.client.message.MessageType
import org.sonarsource.sonarlint.core.clientapi.client.message.ShowMessageParams
import org.sonarsource.sonarlint.core.commons.http.HttpClient
import org.sonarsource.sonarlint.core.commons.log.ClientLogOutput
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent
import javax.swing.SwingUtilities

object SonarLintIntelliJClient : SonarLintClient {

    private val GROUP: NotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("SonarLint")

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

    override fun getHttpClientNoAuth(forUrl: String): HttpClient? {
        return default
    }

    override fun openUrlInBrowser(params: OpenUrlInBrowserParams) {
        BrowserUtil.browse(params.url)
    }

    override fun showMessage(params: ShowMessageParams) {
        showBalloon(null, params.text, convert(params.type))
    }

    private fun convert(type: String?): NotificationType {
        if (type == MessageType.ERROR.name) return NotificationType.ERROR
        if (type == MessageType.WARNING.name) return NotificationType.WARNING
        return NotificationType.INFORMATION
    }

    private fun showBalloon(project: Project?, message: String, type: NotificationType) {
        val notification = GROUP.createNotification(
            "SonarLint message",
            message,
            type
        )
        notification.isImportant = type != NotificationType.INFORMATION
        notification.notify(project)
    }

    override fun getHostInfo(): CompletableFuture<GetHostInfoResponse> {
        var description = ApplicationInfo.getInstance().fullVersion
        val edition = ApplicationNamesInfo.getInstance().editionName
        if (edition != null) {
            description += " ($edition)"
        }
        val openProjects = ProjectManager.getInstance().openProjects
        if (openProjects.isNotEmpty()) {
            description += " - " + openProjects.joinToString(", ") { it.name }
        }
        val hostInfoDto = GetHostInfoResponse(description)
        return CompletableFuture.completedFuture(hostInfoDto)
    }

    override fun showHotspot(params: ShowHotspotParams) {
        val configurationScopeId = params.configurationScopeId
        val project =
            findProject(configurationScopeId) ?: throw IllegalStateException("Unable to find project with id '$configurationScopeId'")
        SonarLintProjectNotifications.get(project).expireCurrentHotspotNotificationIfNeeded()
        val file = tryFindFile(project, params.hotspotDetails.filePath)
        if (file == null) {
            showBalloon(project, "Unable to open security hotspot. Can't find the file: ${params.hotspotDetails.filePath}", NotificationType.WARNING)
            return
        }
        ApplicationManager.getApplication().invokeAndWait {
            openFile(project, file)
        }
        getService(project, AnalysisSubmitter::class.java).analyzeFileAndTrySelectHotspot(file, params.hotspotDetails.key)
    }

    private fun tryFindFile(project: Project, filePath: String): VirtualFile? {
        for (contentRoot in ProjectRootManager.getInstance(project).contentRoots) {
            if (contentRoot.isDirectory) {
                val matchedFile = contentRoot.findFileByRelativePath(filePath)
                if (matchedFile != null) {
                    return matchedFile
                }
            } else {
                // On Rider, all source files are returned as individual content roots, so simply check for equality
                if (contentRoot.path.endsWith(filePath)) {
                    return contentRoot
                }
            }
        }
        return null
    }

    private fun openFile(project: Project, file: VirtualFile) {
        OpenFileDescriptor(project, file).navigate(true)
    }

    override fun assistCreatingConnection(params: AssistCreatingConnectionParams): CompletableFuture<AssistCreatingConnectionResponse> {
        return CompletableFuture.supplyAsync {
            val serverUrl = params.serverUrl
            val message = "No connections configured to '$serverUrl'."
            if (!showConfirmModal("Opening Security Hotspot...", message, "Create connection", null)) {
                throw CancellationException("Connection creation rejected by the user")
            }
            val newConnection = ApplicationManager.getApplication().computeInEDT {
                ServerConnectionCreator().createThroughWizard(serverUrl)
            } ?: throw CancellationException("Connection creation cancelled by the user")
            AssistCreatingConnectionResponse(newConnection.name)
        }
    }

    override fun assistBinding(params: AssistBindingParams): CompletableFuture<AssistBindingResponse> {
        return CompletableFuture.supplyAsync {
            val connectionId = params.connectionId
            val projectKey = params.projectKey
            val connection = getGlobalSettings().getServerConnectionByName(connectionId)
                .orElseThrow { IllegalStateException("Unable to find connection '$connectionId'") }
            val message = "Cannot automatically find a project bound to:\n" +
                "  • Project: $projectKey\n" +
                "  • Connection: $connectionId\n" +
                "Please manually select a project."
            if (!showConfirmModal("Opening Security Hotspot...", message, "Select project", null)) {
                throw CancellationException("Project selection rejected by the user")
            }
            val selectedProject = ApplicationManager.getApplication().computeInEDT {
                ProjectSelectionDialog().selectProject()?.let {
                    ProjectUtil.openOrImport(it, null, false)
                }
            } ?: throw CancellationException("Project selection cancelled by the user")
            val confirmed = showConfirmModal(
                "Opening Security Hotspot...",
                "You are going to bind current project to '${connection.hostUrl}'. Do you agree?",
                "Yes",
                selectedProject
            )
            if (!confirmed) {
                throw CancellationException("Project binding rejected by the user")
            }
            getService(selectedProject, ProjectBindingManager::class.java).bindTo(connection, projectKey, emptyMap())
            AssistBindingResponse(BackendService.projectId(selectedProject))
        }
    }

    private fun showConfirmModal(title: String, message: String, confirmText: String, project: Project?): Boolean {
        return Messages.OK == ApplicationManager.getApplication().computeInEDT {
            Messages.showYesNoDialog(project, StringEscapeUtils.escapeHtml(message), title, confirmText, "Cancel", Messages.getWarningIcon())
        }
    }

}
