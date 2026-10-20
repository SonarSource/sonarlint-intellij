/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.Notifications
import com.intellij.util.messages.MessageBusConnection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests

class SonarLintNotificationGroupsLightTests : AbstractSonarLintLightTests() {

    private lateinit var notifications: MutableList<Notification>
    private lateinit var busConnection: MessageBusConnection

    @BeforeEach
    fun subscribe() {
        notifications = Lists.newCopyOnWriteArrayList()
        busConnection = project.messageBus.connect(project)
        busConnection.subscribe(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) {
                notifications.add(notification)
            }
        })
    }

    @AfterEach
    fun disconnect() {
        busConnection.disconnect()
    }

    @Test
    fun should_load_balloon_groups_from_plugin_extension_point() {
        val manager = NotificationGroupManager.getInstance()
        assertThat(manager.getNotificationGroup("SonarQube for IDE")).isNotNull
        assertThat(manager.getNotificationGroup("SonarQube for IDE: Secrets detection")).isNotNull
    }

    @Test
    fun should_notify_secret_detection_through_production_service_and_group() {
        SonarLintProjectNotifications(project).sendNotification()

        assertThat(notifications).hasSize(1)
        assertThat(notifications[0].groupId).contains("Secrets detection")
    }
}
