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
package org.sonarlint.intellij.util

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationType.ERROR
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.okCancel
import com.intellij.openapi.vfs.VfsUtil
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import org.sonarlint.intellij.actions.filters.AutoShareTokenExchangeAction
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.common.util.SonarLintUtils.isRider
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.BackendService.Companion.projectId
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.core.ProjectBindingManager.BindingMode
import org.sonarlint.intellij.documentation.SonarLintDocumentation
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications.Companion.get
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetSharedConnectedModeConfigFileResponse
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.ConnectionSuggestionDto

class ConfigurationSharing {

    companion object {
        @JvmStatic
        fun exportConfiguration(project: Project?, modalityState: ModalityState) {
            if (project == null || project.isDisposed) return

            if (confirm(project)) {
                val configScopeId = projectId(project)
                createFile(configScopeId, project, modalityState)
            }
        }

        private fun createFile(configScopeId: String, project: Project, modalityState: ModalityState) {
            val root: Path = project.basePath?.let { Paths.get(it) } ?: return

            getService(BackendService::class.java)
                .getSharedConnectedModeConfigFileContents(configScopeId)
                .thenAcceptAsync { sharedFileContent: GetSharedConnectedModeConfigFileResponse ->
                    val filename = if (isRider()) {
                        project.name + ".json"
                    } else {
                        "connectedMode.json"
                    }

                    try {
                        runOnUiThread(project, modalityState) {
                            ApplicationManager.getApplication().runWriteAction {
                                VfsUtil.createDirectoryIfMissing(root.resolve(".sonarlint").toString())

                                val sonarlintDir = VfsUtil.findFile(root.resolve(".sonarlint"), true)
                                if (sonarlintDir != null) {
                                    val sonarlintFile = sonarlintDir.createChildData(project, filename)
                                    sonarlintFile.refresh(true, false) {
                                        sonarlintFile.setBinaryContent(sharedFileContent.jsonFileContent.toByteArray())
                                    }
                                }

                                get(project).simpleNotification(
                                    null,
                                    "File \'$filename\' has been created",
                                    NotificationType.INFORMATION
                                )
                            }
                        }
                    } catch (e: IOException) {
                        get(project).simpleNotification(
                            null,
                            "Could not create the file '$filename', please check the logs for more details",
                            ERROR
                        )
                        SonarLintConsole.get(project).error("Error while creating the shared file, IO exception : " + e.message)
                    }
                }
        }

        @JvmStatic
        fun showAutoSharedConfigurationNotification(
            project: Project, message: String, doNotShowAgainId: String,
            connectionSuggestionDto: ConnectionSuggestionDto, bindingMode: BindingMode,
        ) {
            if (!PropertiesComponent.getInstance().getBoolean(doNotShowAgainId)) {
                get(project).showAutoSharedConfigurationNotification(
                    "",
                    message,
                    doNotShowAgainId,
                    AutoShareTokenExchangeAction("Use configuration", connectionSuggestionDto, project, bindingMode)
                )
            }
        }

        private fun confirm(project: Project): Boolean {
            val binding = getService(project, ProjectBindingManager::class.java).getBinding() ?: run {
                SonarLintConsole.get(project).error("Binding is not available")
                return false
            }

            val connectionKind = getService(project, ProjectBindingManager::class.java)
                .tryGetServerConnection().map { if (it.isSonarCloud) "SonarCloud organization" else "SonarQube server" }.orElse(null)
                ?: run {
                    SonarLintConsole.get(project).error("Connection is not present")
                    return false
                }

            val projectKey = binding.projectKey
            val connectionName = binding.connectionName
            return okCancel(
                "Share This Connected Mode Configuration?",
                """
                    A configuration file 'connectedMode.json' will be created in your local repository with a reference to project '$projectKey' on $connectionKind '$connectionName'
                    
                    This will help other team members configure the binding for the same project.
                    <a href="${SonarLintDocumentation.Intellij.CONNECTED_MODE_BENEFITS_LINK}">Learn more</a> """.trimIndent()
            )
                .yesText("Share Configuration")
                .noText("Cancel")
                .ask(project)
        }
    }
}
