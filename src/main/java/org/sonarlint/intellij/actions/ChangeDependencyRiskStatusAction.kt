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
package org.sonarlint.intellij.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.finding.sca.LocalDependencyRisk
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread
import org.sonarlint.intellij.ui.resolve.ChangeDependencyRiskStatusDialog
import org.sonarlint.intellij.util.DataKeys.Companion.DEPENDENCY_RISK_DATA_KEY
import org.sonarlint.intellij.util.runOnPooledThread
import org.sonarsource.sonarlint.core.client.utils.DependencyRiskTransitionStatus
import org.sonarsource.sonarlint.core.rpc.protocol.backend.sca.DependencyRiskTransition

class ChangeDependencyRiskStatusAction() : AbstractSonarAction(
    "Change Dependency Risk Status\u2026", "Change the dependency risk status", null
) {

    companion object {
        const val CHANGE_STATUS_GROUP = "SonarQube for IDE: Change Dependency Risk Status"
        private const val ERROR_TITLE = "<b>SonarQube for IDE - Unable to change dependency risk status</b>"
        private const val SUCCESS_CONTENT = "The dependency risk status was successfully changed"

        fun canChangeStatus(project: Project, dependencyRisk: LocalDependencyRisk): Boolean {
            val serverConnection = serverConnection(project)
            return dependencyRisk.canChangeStatus() && serverConnection != null
        }

        fun openChangeDependencyRiskStatusDialogAsync(project: Project, dependencyRisk: LocalDependencyRisk) {
            runOnPooledThread(project) {
                openChangeDependencyRiskStatusDialog(project, dependencyRisk)
            }
        }

        private fun openChangeDependencyRiskStatusDialog(project: Project, dependencyRisk: LocalDependencyRisk) {
            val connection = serverConnection(project) ?: return displayNotificationError(project, "No connection could be found")

            val availableTransitions = dependencyRisk.transitions.map { DependencyRiskTransitionStatus.fromDto(it) }
            
            if (availableTransitions.isEmpty()) {
                return displayNotificationError(project, "You cannot change the status of this dependency risk")
            }

            runOnUiThread(project) {
                val statusChange = ChangeDependencyRiskStatusDialog(
                    project,
                    connection,
                    dependencyRisk.status,
                    availableTransitions,
                ).chooseStatusChange() ?: return@runOnUiThread

                runOnPooledThread(project) {
                    changeDependencyRiskStatus(project, dependencyRisk, statusChange.newStatus, statusChange.comment)
                }
            }
        }

        private fun displayNotificationError(project: Project, content: String) {
            return SonarLintProjectNotifications.get(project).displayErrorNotification(
                ERROR_TITLE,
                content,
                NotificationGroupManager.getInstance().getNotificationGroup(CHANGE_STATUS_GROUP)
            )
        }

        private fun changeDependencyRiskStatus(
            project: Project,
            dependencyRisk: LocalDependencyRisk,
            statusChange: DependencyRiskTransition,
            comment: String?
        ) {
            getService(BackendService::class.java).changeStatusForDependencyRisk(project, dependencyRisk.id, statusChange, comment)
                .thenAcceptAsync {
                    SonarLintProjectNotifications.get(project).displaySuccessfulNotification(
                        SUCCESS_CONTENT,
                        NotificationGroupManager.getInstance().getNotificationGroup(CHANGE_STATUS_GROUP)
                    )
                }
                .exceptionally { error ->
                    SonarLintConsole.get(project).error("Error while changing the dependency risk status", error)
                    SonarLintProjectNotifications.get(project).displayErrorNotification(
                        "Could not change the dependency risk status",
                        NotificationGroupManager.getInstance().getNotificationGroup(CHANGE_STATUS_GROUP)
                    )
                    null
                }
        }

        private fun serverConnection(project: Project): ServerConnection? = getService(
            project, ProjectBindingManager::class.java
        ).tryGetServerConnection().orElse(null)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dependencyRisk = e.getData(DEPENDENCY_RISK_DATA_KEY)
        
        if (dependencyRisk == null) {
            return SonarLintProjectNotifications.get(project)
                .displayErrorNotification(
                    ERROR_TITLE,
                    "The dependency risk could not be found",
                    NotificationGroupManager.getInstance().getNotificationGroup(CHANGE_STATUS_GROUP)
                )
        }

        openChangeDependencyRiskStatusDialogAsync(project, dependencyRisk)
    }

    override fun isVisible(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        val dependencyRisk = e.getData(DEPENDENCY_RISK_DATA_KEY) ?: return false
        return canChangeStatus(project, dependencyRisk)
    }

} 
