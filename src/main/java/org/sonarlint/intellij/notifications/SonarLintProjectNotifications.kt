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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Stream
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.actions.OpenInBrowserAction
import org.sonarlint.intellij.actions.OpenTrackedLinkAction
import org.sonarlint.intellij.actions.ShareConfigurationAction
import org.sonarlint.intellij.actions.ShowLogAction
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.ProjectBindingManager.BindingMode.AUTOMATIC
import org.sonarlint.intellij.core.ProjectBindingManager.BindingMode.IMPORTED
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
        private const val TITLE_SONARLINT_INVALID_BINDING = "<b>SonarQube for IntelliJ - Invalid binding</b>"
        private const val TITLE_SONARLINT_SUGGESTIONS = "<b>SonarQube for IntelliJ suggestions</b>"

        fun get(project: Project): SonarLintProjectNotifications {
            return SonarLintUtils.getService(project, SonarLintProjectNotifications::class.java)
        }

        fun projectLessNotification(title: String?, message: String, type: NotificationType, action: AnAction? = null): Notification {
            return NotificationGroupManager.getInstance().getNotificationGroup("SonarQube for IntelliJ").createNotification(
                title ?: "",
                message,
                type
            ).apply {
                isImportant = type != NotificationType.INFORMATION
                icon = SonarLintIcons.SONARQUBE_FOR_INTELLIJ
                action?.let { addAction(it) }
                notify(null)
            }
        }
    }

    private val inContextPromotionGroup =
        NotificationGroupManager.getInstance().getNotificationGroup("SonarQube for IntelliJ: In Context Promotions")
    private val bindingProblemGroup =
        NotificationGroupManager.getInstance().getNotificationGroup("SonarQube for IntelliJ: Server Binding Errors")
    private val serverNotificationsGroup =
        NotificationGroupManager.getInstance().getNotificationGroup("SonarQube for IntelliJ: Server Notifications")
    private val bindingSuggestionGroup =
        NotificationGroupManager.getInstance().getNotificationGroup("SonarQube for IntelliJ: Binding Suggestions")
    private val openInIdeGroup: NotificationGroup =
        NotificationGroupManager.getInstance().getNotificationGroup("SonarQube for IntelliJ: Open in IDE")
    private val secretDetectionGroup =
        NotificationGroupManager.getInstance().getNotificationGroup("SonarQube for IntelliJ: Secrets detection")
    private val taintGroup = NotificationGroupManager.getInstance().getNotificationGroup("SonarQube for IntelliJ: Taint vulnerabilities")
    private val analyzerRequirementGroup =
        NotificationGroupManager.getInstance().getNotificationGroup("SonarQube for IntelliJ: Analyzer Requirement")
    private val sonarlintGroup = NotificationGroupManager.getInstance().getNotificationGroup("SonarQube for IntelliJ")

    private var storageErrorNotificationShown = AtomicBoolean(false)
    private var lastBindingSuggestion: Notification? = null
    private var currentOpenFindingNotification: Notification? = null

    fun reset() {
        storageErrorNotificationShown.set(false)
    }

    fun notifyProjectBindingInvalidAndUnbound() {
        if (storageErrorNotificationShown.getAndSet(true)) {
            return
        }
        bindingProblemGroup.createNotification(
            TITLE_SONARLINT_INVALID_BINDING,
            "Project binding is invalid and has been removed, the connection has probably been deleted previously.",
            NotificationType.WARNING
        ).apply {
            icon = SonarLintIcons.SONARQUBE_FOR_INTELLIJ
            addAction(OpenProjectSettingsAction(myProject))
            isImportant = true
            notify(myProject)
        }
    }

    fun notifyLanguagePromotion(content: String) {
        inContextPromotionGroup.createNotification(
            TITLE_SONARLINT_SUGGESTIONS,
            content,
            NotificationType.INFORMATION
        ).apply {
            addAction(OpenTrackedLinkAction("Try SonarQube Cloud for free", LinkTelemetry.SONARCLOUD_SIGNUP_PAGE))
            addAction(OpenTrackedLinkAction("Download SonarQube Server", LinkTelemetry.SONARQUBE_EDITIONS_DOWNLOADS))
            addAction(OpenInBrowserAction("Learn more", null, CONNECTED_MODE_BENEFITS_LINK))
            addAction(DontAskAgainAction())
            icon = SonarLintIcons.SONARQUBE_FOR_INTELLIJ
            notify(myProject)
        }
    }

    fun suggestBindingOptions(suggestedBindings: List<BindingSuggestion>) {
        if (suggestedBindings.size == 1) {
            val suggestedBinding = suggestedBindings[0]
            val mode =
                if (suggestedBinding.isFromSharedConfiguration) IMPORTED else AUTOMATIC
            notifyBindingSuggestions(
                "Bind this project to '${suggestedBinding.projectName}' on '${suggestedBinding.connectionId}'?",
                BindProjectAction(suggestedBinding, mode), OpenProjectSettingsAction(myProject, "Select another one")
            )
        } else {
            notifyBindingSuggestions(
                "Bind this project to SonarQube (Server, Cloud)?",
                if (suggestedBindings.isEmpty()) OpenProjectSettingsAction(
                    myProject,
                    "Configure binding"
                ) else ChooseBindingSuggestionAction(suggestedBindings)
            )
        }
    }

    private fun notifyBindingSuggestions(message: String, vararg mainActions: AnAction) {
        expireCurrentBindingSuggestionIfNeeded()
        lastBindingSuggestion = bindingSuggestionGroup.createNotification(
            TITLE_SONARLINT_SUGGESTIONS,
            message,
            NotificationType.INFORMATION
        ).apply {
            Arrays.stream(mainActions).forEach { action: AnAction -> addAction(action) }
            addAction(OpenInBrowserAction("Learn more", null, CONNECTED_MODE_LINK))
            addAction(DisableBindingSuggestionsAction())
            collapseDirection = Notification.CollapseActionsDirection.KEEP_LEFTMOST
            isImportant = true
            icon = SonarLintIcons.SONARQUBE_FOR_INTELLIJ
            notify(myProject)
        }
    }

    fun notifyUnableToOpenFinding(type: String, message: String, vararg mainActions: AnAction) {
        expireCurrentFindingNotificationIfNeeded()
        currentOpenFindingNotification = openInIdeGroup.createNotification(
            "<b>SonarQube for IntelliJ - Unable to open $type</b>",
            message,
            NotificationType.INFORMATION
        ).apply {
            Arrays.stream(mainActions).forEach { action: AnAction -> addAction(action) }
            isImportant = true
            icon = SonarLintIcons.SONARQUBE_FOR_INTELLIJ
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

        val label = if (isSonarCloud) "SonarQube Cloud" else "SonarQube Server"
        serverNotificationsGroup.createNotification(
            "<b>$label Notification</b>",
            smartNotificationParams.text,
            NotificationType.INFORMATION
        ).apply {
            icon = if (isSonarCloud) {
                SonarLintIcons.ICON_SONARQUBE_CLOUD_16
            } else {
                SonarLintIcons.ICON_SONARQUBE_SERVER_16
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
            icon = SonarLintIcons.SONARQUBE_FOR_INTELLIJ
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
            icon = SonarLintIcons.SONARQUBE_FOR_INTELLIJ
            isImportant = true
            addAction(ShowLogAction())
            notify(myProject)
        }
    }

    fun displayWarningNotification(content: String, group: NotificationGroup) {
        group.createNotification(
            "", content, NotificationType.WARNING
        ).apply {
            icon = SonarLintIcons.SONARQUBE_FOR_INTELLIJ
            isImportant = true
            addAction(ShowLogAction())
            notify(myProject)
        }
    }

    fun sendNotification() {
        secretDetectionGroup.createNotification(
            "SonarQube for IntelliJ: secret(s) detected",
            "SonarQube for IntelliJ detected some secrets in one of the open files. " +
                "We strongly advise you to review those secrets and ensure they are not committed into repositories. " +
                "Please refer to the SonarQube for IntelliJ tool window for more information.",
            NotificationType.WARNING
        ).apply {
            icon = SonarLintIcons.SONARQUBE_FOR_INTELLIJ
            setImportant(true)
            notify(myProject)
        }
    }

    fun showTaintNotification(message: String, action: AnAction) {
        taintGroup.createNotification(
            "Taint vulnerabilities",
            message,
            NotificationType.ERROR
        ).apply {
            icon = SonarLintIcons.SONARQUBE_FOR_INTELLIJ
            isImportant = true
            addAction(action)
            notify(myProject)
        }
    }

    fun createNotificationOnce(title: String, content: String, vararg actions: NotificationAction) {
        analyzerRequirementGroup.createNotification(
            title,
            content,
            NotificationType.WARNING
        ).apply {
            icon = SonarLintIcons.SONARQUBE_FOR_INTELLIJ
            setImportant(true)
            Stream.of(*actions).forEach(this::addAction)
            notify(myProject)
        }
    }

    fun showOneTimeBalloon(message: String, doNotShowAgainId: String, action: AnAction?) {
        sonarlintGroup.createNotification(
            "",
            message,
            NotificationType.WARNING
        ).apply {
            icon = SonarLintIcons.SONARQUBE_FOR_INTELLIJ
            action?.let { addAction(it) }
            addAction(DontShowAgainAction(doNotShowAgainId))
            notify(myProject)
        }
    }

    fun showSharedConfigurationNotification(title: String, message: String, doNotShowAgainId: String) {
        expireCurrentBindingSuggestionIfNeeded()
        sonarlintGroup.createNotification(
            title,
            message,
            NotificationType.WARNING
        ).apply {
            icon = SonarLintIcons.SONARQUBE_FOR_INTELLIJ
            addAction(ShareConfigurationAction("Share configuration"))
            addAction(DontShowAgainAction(doNotShowAgainId))
            notify(myProject)
        }
    }

    fun showAutoSharedConfigurationNotification(title: String, message: String, doNotShowAgainId: String, action: AnAction?) {
        expireCurrentBindingSuggestionIfNeeded()
        sonarlintGroup.createNotification(
            title,
            message,
            NotificationType.INFORMATION
        ).apply {
            icon = SonarLintIcons.SONARQUBE_FOR_INTELLIJ
            action?.let { addAction(it) }
            addAction(DontShowAgainAction(doNotShowAgainId))
            notify(myProject)
        }
    }

    fun simpleNotification(title: String?, message: String, type: NotificationType, vararg actions: AnAction) {
        sonarlintGroup.createNotification(
            title ?: "",
            message,
            type
        ).apply {
            icon = SonarLintIcons.SONARQUBE_FOR_INTELLIJ
            Stream.of(*actions).forEach(this::addAction)
            notify(myProject)
        }
    }

}
