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
package org.sonarlint.intellij.notifications

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.Arrays
import java.util.stream.Stream
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.actions.OpenInBrowserAction
import org.sonarlint.intellij.actions.OpenTrackedLinkAction
import org.sonarlint.intellij.actions.ShowLogAction
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.CONNECTED_MODE_BENEFITS_LINK
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.CONNECTED_MODE_LINK
import org.sonarlint.intellij.notifications.binding.BindProjectAction
import org.sonarlint.intellij.notifications.binding.BindingSuggestion
import org.sonarlint.intellij.notifications.binding.ChooseBindingSuggestionAction
import org.sonarlint.intellij.notifications.binding.DisableBindingSuggestionsAction
import org.sonarlint.intellij.promotion.DontAskAgainAction
import org.sonarlint.intellij.telemetry.LinkTelemetry
import org.sonarlint.intellij.util.GlobalLogOutput
import org.sonarsource.sonarlint.core.client.utils.ClientLogOutput
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams


@Service(Service.Level.PROJECT)
class SonarLintProjectNotifications(private val myProject: Project) {

    companion object {
        val IN_CONTEXT_PROMOTION_GROUP: NotificationGroup =
            NotificationGroupManager.getInstance().getNotificationGroup("SonarLint: In Context Promotions")
        val BINDING_PROBLEM_GROUP: NotificationGroup =
            NotificationGroupManager.getInstance().getNotificationGroup("SonarLint: Server Binding Errors")
        val SERVER_NOTIFICATIONS_GROUP: NotificationGroup =
            NotificationGroupManager.getInstance().getNotificationGroup("SonarLint: Server Notifications")
        val BINDING_SUGGESTION_GROUP: NotificationGroup =
            NotificationGroupManager.getInstance().getNotificationGroup("SonarLint: Binding Suggestions")
        val OPEN_IN_IDE_GROUP: NotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("SonarLint: Open in IDE")
        val SECRET_DETECTION_GROUP: NotificationGroup =
            NotificationGroupManager.getInstance().getNotificationGroup("SonarLint: Secrets detection")
        val TAINT_GROUP: NotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("SonarLint: Taint vulnerabilities")
        val ANALYZER_REQUIREMENT_GROUP: NotificationGroup =
            NotificationGroupManager.getInstance().getNotificationGroup("SonarLint: Analyzer Requirement")
        val ISSUE_RESOLVED_GROUP: NotificationGroup =
            NotificationGroupManager.getInstance().getNotificationGroup("SonarLint: Mark Issue as Resolved")
        val HOTSPOT_REVIEW_GROUP: NotificationGroup =
            NotificationGroupManager.getInstance().getNotificationGroup("SonarLint: Security Hotspot Review")
        val SONARLINT_GROUP: NotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("SonarLint")

        private const val UPDATE_BINDING_MSG = "\n<br>Please check the SonarLint project configuration"
        private const val TITLE_SONARLINT_INVALID_BINDING = "<b>SonarLint - Invalid binding</b>"
        private const val TITLE_SONARLINT_SUGGESTIONS = "<b>SonarLint suggestions</b>"

        fun get(project: Project): SonarLintProjectNotifications {
            return SonarLintUtils.getService(project, SonarLintProjectNotifications::class.java)
        }

        fun projectLessNotification(title: String?, message: String, type: NotificationType, action: AnAction? = null): Notification {
            return SONARLINT_GROUP.createNotification(
                title ?: "",
                message,
                type
            ).apply {
                isImportant = type != NotificationType.INFORMATION
                icon = SonarLintIcons.SONARLINT
                action?.let { addAction(it) }
                notify(null)
            }
        }
    }

    @Volatile
    private var storageErrorNotificationShown = false

    private var lastBindingSuggestion: Notification? = null
    private var currentOpenFindingNotification: Notification? = null

    fun reset() {
        storageErrorNotificationShown = false
    }

    fun notifyConnectionIdInvalid() {
        if (storageErrorNotificationShown) {
            return
        }
        BINDING_PROBLEM_GROUP.createNotification(
            TITLE_SONARLINT_INVALID_BINDING,
            "Project bound to an invalid connection $UPDATE_BINDING_MSG",
            NotificationType.WARNING
        ).apply {
            icon = SonarLintIcons.SONARLINT
            addAction(OpenProjectSettingsAction(myProject))
            isImportant = true
            notify(myProject)
        }
        storageErrorNotificationShown = true
    }

    fun notifyProjectStorageInvalid() {
        if (storageErrorNotificationShown) {
            return
        }
        BINDING_PROBLEM_GROUP.createNotification(
            TITLE_SONARLINT_INVALID_BINDING,
            "Project bound to an invalid remote project $UPDATE_BINDING_MSG",
            NotificationType.WARNING
        ).apply {
            icon = SonarLintIcons.SONARLINT
            addAction(OpenProjectSettingsAction(myProject))
            isImportant = true
            notify(myProject)
        }
        storageErrorNotificationShown = true
    }

    fun notifyProjectBindingInvalidAndUnbound() {
        if (storageErrorNotificationShown) {
            return
        }
        BINDING_PROBLEM_GROUP.createNotification(
            TITLE_SONARLINT_INVALID_BINDING,
            "Project binding is invalid and has been removed, the connection has probably been deleted previously.",
            NotificationType.WARNING
        ).apply {
            icon = SonarLintIcons.SONARLINT
            addAction(OpenProjectSettingsAction(myProject))
            isImportant = true
            notify(myProject)
        }
        storageErrorNotificationShown = true
    }

    fun notifyLanguagePromotion(content: String) {
        IN_CONTEXT_PROMOTION_GROUP.createNotification(
            TITLE_SONARLINT_SUGGESTIONS,
            content,
            NotificationType.INFORMATION
        ).apply {
            addAction(OpenTrackedLinkAction("Try SonarCloud for free", LinkTelemetry.SONARCLOUD_SIGNUP_PAGE))
            addAction(OpenTrackedLinkAction("Download SonarQube", LinkTelemetry.SONARQUBE_EDITIONS_DOWNLOADS))
            addAction(OpenInBrowserAction("Learn more", null, CONNECTED_MODE_BENEFITS_LINK))
            addAction(DontAskAgainAction())
            icon = SonarLintIcons.SONARLINT
            notify(myProject)
        }
    }

    fun suggestBindingOptions(suggestedBindings: List<BindingSuggestion>) {
        if (suggestedBindings.size == 1) {
            val suggestedBinding = suggestedBindings[0]
            notifyBindingSuggestions(
                "Bind this project to '${suggestedBinding.projectName}' on '${suggestedBinding.connectionId}'?",
                BindProjectAction(suggestedBinding), OpenProjectSettingsAction(myProject, "Select another one")
            )
        } else {
            notifyBindingSuggestions(
                "Bind this project to SonarCloud or SonarQube?",
                if (suggestedBindings.isEmpty()) OpenProjectSettingsAction(
                    myProject,
                    "Configure binding"
                ) else ChooseBindingSuggestionAction(suggestedBindings)
            )
        }
    }

    private fun notifyBindingSuggestions(message: String, vararg mainActions: AnAction) {
        expireCurrentBindingSuggestionIfNeeded()
        lastBindingSuggestion = BINDING_SUGGESTION_GROUP.createNotification(
            TITLE_SONARLINT_SUGGESTIONS,
            message,
            NotificationType.INFORMATION
        ).apply {
            Arrays.stream(mainActions).forEach { action: AnAction -> addAction(action) }
            addAction(OpenInBrowserAction("Learn more", null, CONNECTED_MODE_LINK))
            addAction(DisableBindingSuggestionsAction())
            collapseDirection = Notification.CollapseActionsDirection.KEEP_LEFTMOST
            isImportant = true
            icon = SonarLintIcons.SONARLINT
            notify(myProject)
        }
    }

    fun notifyUnableToOpenFinding(type: String, message: String, vararg mainActions: AnAction) {
        expireCurrentFindingNotificationIfNeeded()
        currentOpenFindingNotification = OPEN_IN_IDE_GROUP.createNotification(
            "<b>SonarLint - Unable to open $type</b>",
            message,
            NotificationType.INFORMATION
        ).apply {
            Arrays.stream(mainActions).forEach { action: AnAction -> addAction(action) }
            isImportant = true
            icon = SonarLintIcons.SONARLINT
            notify(myProject)
        }
    }

    fun expireCurrentFindingNotificationIfNeeded() {
        if (currentOpenFindingNotification != null) {
            currentOpenFindingNotification!!.expire()
            currentOpenFindingNotification = null
        }
    }

    private fun expireCurrentBindingSuggestionIfNeeded() {
        lastBindingSuggestion?.expire()
        lastBindingSuggestion = null
    }

    fun handle(smartNotificationParams: ShowSmartNotificationParams) {
        val connection = Settings.getGlobalSettings().getServerConnectionByName(smartNotificationParams.connectionId)
        if (connection.isEmpty) {
            GlobalLogOutput.get().log("Connection ID of smart notification should not be null", ClientLogOutput.Level.WARN)
            return
        }
        val isSonarCloud = connection.map { obj: ServerConnection -> obj.isSonarCloud }.orElse(false)

        val label = if (isSonarCloud) "SonarCloud" else "SonarQube"
        SERVER_NOTIFICATIONS_GROUP.createNotification(
            "<b>$label Notification</b>",
            smartNotificationParams.text,
            NotificationType.INFORMATION
        ).apply {
            icon = if (isSonarCloud) {
                SonarLintIcons.ICON_SONARCLOUD_16
            } else {
                SonarLintIcons.ICON_SONARQUBE_16
            }
            isImportant = true
            addAction(OpenInServerAction(label, smartNotificationParams.link, smartNotificationParams.category))
            addAction(ConfigureNotificationsAction(connection.get().name, myProject))
            notify(myProject)
        }
    }

    fun displaySuccessfulNotification(content: String, group: NotificationGroup) {
        group.createNotification(
            "",
            content,
            NotificationType.INFORMATION
        ).apply {
            icon = SonarLintIcons.SONARLINT
            isImportant = true
            notify(myProject)
        }
    }

    fun displayErrorNotification(content: String, group: NotificationGroup) {
        displayErrorNotification("", content, group)
    }

    fun displayErrorNotification(title: String, content: String, group: NotificationGroup) {
        group.createNotification(
            title, content, NotificationType.ERROR
        ).apply {
            icon = SonarLintIcons.SONARLINT
            isImportant = true
            addAction(ShowLogAction())
            notify(myProject)
        }
    }

    fun displayWarningNotification(content: String, group: NotificationGroup) {
        group.createNotification(
            "", content, NotificationType.WARNING
        ).apply {
            icon = SonarLintIcons.SONARLINT
            isImportant = true
            addAction(ShowLogAction())
            notify(myProject)
        }
    }

    fun sendNotification() {
        SECRET_DETECTION_GROUP.createNotification(
            "SonarLint: secret(s) detected",
            "SonarLint detected some secrets in one of the open files. " +
                "We strongly advise you to review those secrets and ensure they are not committed into repositories. " +
                "Please refer to the SonarLint tool window for more information.",
            NotificationType.WARNING
        ).apply {
            icon = SonarLintIcons.SONARLINT
            setImportant(true)
            notify(myProject)
        }
    }

    fun showTaintNotification(message: String, action: AnAction) {
        TAINT_GROUP.createNotification(
            "Taint vulnerabilities",
            message,
            NotificationType.ERROR
        ).apply {
            icon = SonarLintIcons.SONARLINT
            isImportant = true
            addAction(action)
            notify(myProject)
        }
    }

    fun createNotificationOnce(title: String, content: String, vararg actions: NotificationAction) {
        ANALYZER_REQUIREMENT_GROUP.createNotification(
            title,
            content,
            NotificationType.WARNING
        ).apply {
            icon = SonarLintIcons.SONARLINT
            setImportant(true)
            Stream.of(*actions).forEach(this::addAction)
            notify(myProject)
        }
    }

    fun showOneTimeBalloon(message: String, doNotShowAgainId: String, action: AnAction?) {
        SONARLINT_GROUP.createNotification(
            "",
            message,
            NotificationType.WARNING
        ).apply {
            icon = SonarLintIcons.SONARLINT
            action?.let { addAction(it) }
            addAction(DontShowAgainAction(doNotShowAgainId))
            notify(myProject)
        }
    }

    fun showSharedConfigurationNotification(title: String,message: String, doNotShowAgainId: String, action: AnAction?) {
        SONARLINT_GROUP.createNotification(
            title,
            message,
            NotificationType.WARNING
        ).apply {
            icon = SonarLintIcons.SONARLINT
            action?.let { addAction(it) }
            addAction(DontShowAgainAction(doNotShowAgainId))
            notify(myProject)
        }
    }

    fun simpleNotification(title: String?, message: String, type: NotificationType, vararg actions: AnAction) {
        SONARLINT_GROUP.createNotification(
            title ?: "",
            message,
            type
        ).apply {
            icon = SonarLintIcons.SONARLINT
            Stream.of(*actions).forEach(this::addAction)
            notify(myProject)
        }
    }

}
