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
package org.sonarlint.intellij.sharing

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationType.ERROR
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.okCancel
import com.intellij.openapi.vfs.VfsUtil
import java.io.IOException
import org.sonarlint.intellij.actions.filters.AutoShareTokenExchangeAction
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.common.util.SonarLintUtils.isRider
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.documentation.SonarLintDocumentation
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications.Companion.get
import org.sonarlint.intellij.sharing.SharedConnectedModeUtils.Companion.findSharedFolder
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.util.runOnPooledThread
import org.sonarsource.sonarlint.core.rpc.protocol.backend.binding.GetSharedConnectedModeConfigFileResponse
import org.sonarsource.sonarlint.core.rpc.protocol.client.connection.ConnectionSuggestionDto

class ConfigurationSharing {

    companion object {
        @JvmStatic
        fun shareConfiguration(project: Project?, modalityState: ModalityState) {
            if (project == null || project.isDisposed) return

            if (confirm(project)) {
                runOnPooledThread(project) {
                    createFile(project, modalityState)

                    getService(project, ProjectBindingManager::class.java).getBinding()?.moduleBindingsOverrides?.forEach { override ->
                        createFile(override.key, modalityState)
                    }
                }
            }
        }

        private fun createFile(project: Project, modalityState: ModalityState) {
            val sonarlintFolder = findSharedFolder(project)

            if (sonarlintFolder == null) {
                get(project).simpleNotification(
                    null,
                    "Could not find the directory where to store the configuration file",
                    ERROR
                )
                return
            }

            getService(BackendService::class.java)
                .getSharedConnectedModeConfigFileContents(project)
                .thenAcceptAsync { sharedFileContent: GetSharedConnectedModeConfigFileResponse ->
                    val filename = if (isRider()) {
                        "${project.name}.json"
                    } else {
                        "connectedMode.json"
                    }

                    try {
                        runOnUiThread(project, modalityState) {
                            ApplicationManager.getApplication().runWriteAction {
                                VfsUtil.createDirectoryIfMissing(sonarlintFolder.toString())

                                val sonarlintDir = VfsUtil.findFile(sonarlintFolder, true)
                                if (sonarlintDir != null) {
                                    val sonarlintFile = sonarlintDir.createChildData(project, filename)
                                    sonarlintFile.refresh(true, false) {
                                        sonarlintFile.setBinaryContent(sharedFileContent.jsonFileContent.toByteArray())
                                    }
                                }

                                get(project).simpleNotification(
                                    null,
                                    "File '$filename' has been created",
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
                        SonarLintConsole.get(project).error("Error while creating the shared connected mode file", e)
                    }
                }
        }

        // Don't send notifications on file creation for module, it could generate a lot of noise
        private fun createFile(module: Module, modalityState: ModalityState) {
            val sonarlintFolder = findSharedFolder(module)

            if (sonarlintFolder == null) {
                SonarLintConsole.get(module.project).error("Could not find the directory where to store the configuration file for module ${module.name}")
                return
            }

            getService(BackendService::class.java)
                .getSharedConnectedModeConfigFileContents(module)
                .thenAcceptAsync { sharedFileContent: GetSharedConnectedModeConfigFileResponse ->
                    val filename = if (isRider()) {
                        "${module.name}.json"
                    } else {
                        "connectedMode.json"
                    }

                    try {
                        runOnUiThread(module.project, modalityState) {
                            ApplicationManager.getApplication().runWriteAction {
                                VfsUtil.createDirectoryIfMissing(sonarlintFolder.toString())

                                val sonarlintDir = VfsUtil.findFile(sonarlintFolder, true)
                                if (sonarlintDir != null) {
                                    val sonarlintFile = sonarlintDir.createChildData(module, filename)
                                    sonarlintFile.refresh(true, false) {
                                        sonarlintFile.setBinaryContent(sharedFileContent.jsonFileContent.toByteArray())
                                    }
                                }
                            }
                        }
                    } catch (e: IOException) {
                        SonarLintConsole.get(module.project).error("Error while creating the shared connected mode file for module ${module.name}", e)
                    }
                }
        }

        @JvmStatic
        fun showAutoSharedConfigurationNotification(
            project: Project, overridesPerModule: Map<Module, String>, message: String, doNotShowAgainId: String,
            connectionSuggestionDto: ConnectionSuggestionDto) {
            if (!PropertiesComponent.getInstance().getBoolean(doNotShowAgainId)) {
                get(project).showAutoSharedConfigurationNotification(
                    "",
                    message,
                    doNotShowAgainId,
                    AutoShareTokenExchangeAction("Use configuration", connectionSuggestionDto, project, overridesPerModule)
                )
            }
        }

        private fun confirm(project: Project): Boolean {
            val binding = getService(project, ProjectBindingManager::class.java).getBinding() ?: run {
                SonarLintConsole.get(project).error("Binding is not available")
                return false
            }

            val moduleOverridesText = if (binding.moduleBindingsOverrides.isNotEmpty()) {
                "Additionally, configuration files will be created for each of the overridden modules in your project.\n"
            } else "\n"

            val connectionKind = getService(project, ProjectBindingManager::class.java)
                .tryGetServerConnection().map { if (it.isSonarCloud) "SonarQube Cloud organization" else "SonarQube Server instance" }
                .orElse(null)
                ?: run {
                    SonarLintConsole.get(project).error("Connection is not present")
                    return false
                }

            val projectKey = binding.projectKey
            val connectionName = binding.connectionName
            return okCancel(
                "Share This Connected Mode Configuration?",
                """
                    A configuration file will be created in the '.sonarlint' folder with a reference to project '$projectKey' on $connectionKind '$connectionName'.
                    $moduleOverridesText
                    This will help other team members configure the binding for the same project.
                    <a href="${SonarLintDocumentation.Intellij.SHARING_CONNECTED_MODE_CONFIGURATION_LINK}">Learn more</a> """.trimIndent()
            )
                .yesText("Share Configuration")
                .noText("Cancel")
                .ask(project)
        }
    }
}
