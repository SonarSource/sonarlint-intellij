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
package org.sonarlint.intellij.notifications

import com.google.common.collect.Lists
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsManager
import com.intellij.util.messages.MessageBusConnection
import java.util.stream.Stream
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.SonarLintIcons
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarsource.sonarlint.core.rpc.protocol.client.smartnotification.ShowSmartNotificationParams

class SonarLintProjectNotificationsTests : AbstractSonarLintLightTests() {

    private lateinit var notifications: MutableList<Notification>
    private lateinit var busConnection: MessageBusConnection
    private lateinit var sonarLintProjectNotifications: SonarLintProjectNotifications

    @BeforeEach
    fun before() {
        sonarLintProjectNotifications = SonarLintProjectNotifications(project)

        // register a listener to catch all notifications
        notifications = Lists.newCopyOnWriteArrayList()
        val project = project
        busConnection = project.messageBus.connect(project)
        busConnection.subscribe(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) {
                notifications.add(notification)
            }
        })
    }

    @AfterEach
    fun expireAfterTest() {
        val mgr = NotificationsManager.getNotificationsManager()
        val notifs = mgr.getNotificationsOfType(Notification::class.java, project)
        Stream.of(*notifs).forEach { notification -> mgr.expire(notification) }
        busConnection.disconnect()
    }

    @Test
    fun should_notify_project_binding_invalid_and_unbound() {
        sonarLintProjectNotifications.notifyProjectBindingInvalidAndUnbound()

        Assertions.assertThat(notifications).hasSize(1)
        Assertions.assertThat(notifications[0].title).isEqualTo("<b>SonarQube for IDE - Invalid binding</b>")
        Assertions.assertThat(notifications[0].content)
            .isEqualTo("Project binding is invalid and has been removed, the connection has probably been deleted previously.")
        Assertions.assertThat(notifications[0].type).isEqualTo(NotificationType.WARNING)
        Assertions.assertThat(notifications[0].icon).isEqualTo(SonarLintIcons.SONARQUBE_FOR_INTELLIJ)
        Assertions.assertThat(notifications[0].actions.size).isEqualTo(1)
    }

    @Test
    fun should_notify_language_promotion() {
        val content = "content"

        sonarLintProjectNotifications.notifyLanguagePromotion(content)

        Assertions.assertThat(notifications).hasSize(1)
        Assertions.assertThat(notifications[0].title).isEqualTo("<b>SonarQube for IDE suggestions</b>")
        Assertions.assertThat(notifications[0].content).isEqualTo(content)
        Assertions.assertThat(notifications[0].type).isEqualTo(NotificationType.INFORMATION)
        Assertions.assertThat(notifications[0].icon).isEqualTo(SonarLintIcons.SONARQUBE_FOR_INTELLIJ)
        Assertions.assertThat(notifications[0].actions.size).isEqualTo(4)
    }

    @Test
    fun should_notify_unable_to_open_finding() {
        val message = "message"

        sonarLintProjectNotifications.notifyUnableToOpenFinding(message)

        Assertions.assertThat(notifications).hasSize(1)
        Assertions.assertThat(notifications[0].title).isBlank()
        Assertions.assertThat(notifications[0].content).isEqualTo(message)
        Assertions.assertThat(notifications[0].type).isEqualTo(NotificationType.INFORMATION)
        Assertions.assertThat(notifications[0].isExpired).isEqualTo(false)
        Assertions.assertThat(notifications[0].icon).isEqualTo(SonarLintIcons.SONARQUBE_FOR_INTELLIJ)
        Assertions.assertThat(notifications[0].actions).isEmpty()
    }

    @Test
    fun should_expire_current_finding_notification() {
        val message = "message"
        sonarLintProjectNotifications.notifyUnableToOpenFinding(message)

        sonarLintProjectNotifications.expireCurrentFindingNotificationIfNeeded()

        Assertions.assertThat(notifications).hasSize(1)
        Assertions.assertThat(notifications[0].isExpired).isEqualTo(true)
    }

    @Test
    fun should_handle_sonarqube_smart_notification() {
        val connection = ServerConnection.newBuilder().setName("connectionId").setHostUrl("url").build()
        globalSettings.serverConnections = listOf(connection)

        val param = ShowSmartNotificationParams(
            "text",
            "link",
            setOf("scopeId"),
            "category",
            "connectionId"
        )

        sonarLintProjectNotifications.handle(param)

        Assertions.assertThat(notifications).hasSize(1)
        Assertions.assertThat(notifications[0].title).isEqualTo("<b>SonarQube Server Notification</b>")
        Assertions.assertThat(notifications[0].content).isEqualTo("text")
        Assertions.assertThat(notifications[0].type).isEqualTo(NotificationType.INFORMATION)
        Assertions.assertThat(notifications[0].icon).isEqualTo(SonarLintIcons.ICON_SONARQUBE_SERVER_16)
        Assertions.assertThat(notifications[0].actions.size).isEqualTo(2)
    }

    @Test
    fun should_handle_sonarcloud_smart_notification() {
        val connection = ServerConnection.newBuilder().setName("connectionId").setHostUrl("https://sonarcloud.io").build()
        globalSettings.serverConnections = listOf(connection)

        val param = ShowSmartNotificationParams(
            "text",
            "link",
            setOf("scopeId"),
            "category",
            "connectionId"
        )

        sonarLintProjectNotifications.handle(param)

        Assertions.assertThat(notifications).hasSize(1)
        Assertions.assertThat(notifications[0].title).isEqualTo("<b>SonarQube Cloud Notification</b>")
        Assertions.assertThat(notifications[0].content).isEqualTo("text")
        Assertions.assertThat(notifications[0].type).isEqualTo(NotificationType.INFORMATION)
        Assertions.assertThat(notifications[0].icon).isEqualTo(SonarLintIcons.ICON_SONARQUBE_CLOUD_16)
        Assertions.assertThat(notifications[0].actions.size).isEqualTo(2)
    }

    @Test
    fun should_display_successful_notification() {
        val content = "content"
        val group = NotificationGroup.allRegisteredGroups.first()

        sonarLintProjectNotifications.displaySuccessfulNotification(content, group)

        Assertions.assertThat(notifications).hasSize(1)
        Assertions.assertThat(notifications[0].title).isBlank()
        Assertions.assertThat(notifications[0].content).isEqualTo(content)
        Assertions.assertThat(notifications[0].type).isEqualTo(NotificationType.INFORMATION)
        Assertions.assertThat(notifications[0].icon).isEqualTo(SonarLintIcons.SONARQUBE_FOR_INTELLIJ)
        Assertions.assertThat(notifications[0].actions).isEmpty()
    }

    @Test
    fun should_display_error_notification() {
        val title = "title"
        val content = "content"
        val group = NotificationGroup.allRegisteredGroups.first()

        sonarLintProjectNotifications.displayErrorNotification(title, content, group)

        Assertions.assertThat(notifications).hasSize(1)
        Assertions.assertThat(notifications[0].title).isEqualTo(title)
        Assertions.assertThat(notifications[0].content).isEqualTo(content)
        Assertions.assertThat(notifications[0].type).isEqualTo(NotificationType.ERROR)
        Assertions.assertThat(notifications[0].icon).isEqualTo(SonarLintIcons.SONARQUBE_FOR_INTELLIJ)
        Assertions.assertThat(notifications[0].actions.size).isEqualTo(1)
    }

    @Test
    fun should_display_warning_notification() {
        val content = "content"
        val group = NotificationGroup.allRegisteredGroups.first()

        sonarLintProjectNotifications.displayWarningNotification(content, group)

        Assertions.assertThat(notifications).hasSize(1)
        Assertions.assertThat(notifications[0].title).isBlank()
        Assertions.assertThat(notifications[0].content).isEqualTo(content)
        Assertions.assertThat(notifications[0].type).isEqualTo(NotificationType.WARNING)
        Assertions.assertThat(notifications[0].icon).isEqualTo(SonarLintIcons.SONARQUBE_FOR_INTELLIJ)
        Assertions.assertThat(notifications[0].actions.size).isEqualTo(1)
    }

    @Test
    fun should_send_notification_for_secret() {
        sonarLintProjectNotifications.sendNotification()

        Assertions.assertThat(notifications).hasSize(1)
        Assertions.assertThat(notifications[0].title).isEqualTo("SonarQube for IDE: secret(s) detected")
        Assertions.assertThat(notifications[0].content).isEqualTo(
            "SonarQube for IDE detected some secrets in one of the open files. " +
                "We strongly advise you to review those secrets and ensure they are not committed into repositories. " +
                "Please refer to the SonarQube for IDE tool window for more information."
        )
        Assertions.assertThat(notifications[0].type).isEqualTo(NotificationType.WARNING)
        Assertions.assertThat(notifications[0].icon).isEqualTo(SonarLintIcons.SONARQUBE_FOR_INTELLIJ)
        Assertions.assertThat(notifications[0].actions).isEmpty()
    }

    @Test
    fun should_display_one_time_balloon() {
        val message = "message"
        val doNotShowAgainId = "id"

        sonarLintProjectNotifications.showOneTimeBalloon(message, doNotShowAgainId, null)

        Assertions.assertThat(notifications).hasSize(1)
        Assertions.assertThat(notifications[0].title).isBlank()
        Assertions.assertThat(notifications[0].content).isEqualTo(message)
        Assertions.assertThat(notifications[0].type).isEqualTo(NotificationType.WARNING)
        Assertions.assertThat(notifications[0].icon).isEqualTo(SonarLintIcons.SONARQUBE_FOR_INTELLIJ)
        Assertions.assertThat(notifications[0].actions.size).isEqualTo(1)
    }

    @Test
    fun should_show_shared_configuration() {
        val title = "title"
        val message = "message"
        val doNotShowAgainId = "id"

        sonarLintProjectNotifications.showSharedConfigurationNotification(title, message, doNotShowAgainId)

        Assertions.assertThat(notifications).hasSize(1)
        Assertions.assertThat(notifications[0].title).isEqualTo(title)
        Assertions.assertThat(notifications[0].content).isEqualTo(message)
        Assertions.assertThat(notifications[0].type).isEqualTo(NotificationType.WARNING)
        Assertions.assertThat(notifications[0].icon).isEqualTo(SonarLintIcons.SONARQUBE_FOR_INTELLIJ)
        Assertions.assertThat(notifications[0].actions.size).isEqualTo(2)
    }

    @Test
    fun should_show_auto_shared_configuration() {
        val title = "title"
        val message = "message"
        val doNotShowAgainId = "id"

        sonarLintProjectNotifications.showAutoSharedConfigurationNotification(title, message, doNotShowAgainId, null)

        Assertions.assertThat(notifications).hasSize(1)
        Assertions.assertThat(notifications[0].title).isEqualTo(title)
        Assertions.assertThat(notifications[0].content).isEqualTo(message)
        Assertions.assertThat(notifications[0].type).isEqualTo(NotificationType.INFORMATION)
        Assertions.assertThat(notifications[0].icon).isEqualTo(SonarLintIcons.SONARQUBE_FOR_INTELLIJ)
        Assertions.assertThat(notifications[0].actions.size).isEqualTo(1)
    }

    @Test
    fun should_display_simple_notification() {
        val title = "title"
        val message = "message"
        val type = NotificationType.INFORMATION

        sonarLintProjectNotifications.simpleNotification(title, message, type)

        Assertions.assertThat(notifications).hasSize(1)
        Assertions.assertThat(notifications[0].title).isEqualTo(title)
        Assertions.assertThat(notifications[0].content).isEqualTo(message)
        Assertions.assertThat(notifications[0].type).isEqualTo(type)
        Assertions.assertThat(notifications[0].icon).isEqualTo(SonarLintIcons.SONARQUBE_FOR_INTELLIJ)
        Assertions.assertThat(notifications[0].actions).isEmpty()
    }

}
