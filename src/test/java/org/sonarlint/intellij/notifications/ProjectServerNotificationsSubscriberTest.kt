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
package org.sonarlint.intellij.notifications

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.any
import org.sonarlint.intellij.capture
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings
import org.sonarlint.intellij.core.ServerNotificationsService
import org.sonarlint.intellij.messages.GlobalConfigurationListener
import org.sonarlint.intellij.messages.ProjectConfigurationListener
import org.sonarlint.intellij.util.ImmediateExecutorService
import org.sonarsource.sonarlint.core.serverconnection.smartnotifications.NotificationConfiguration

class ProjectServerNotificationsSubscriberTest : AbstractSonarLintLightTests() {
  private lateinit var serverNotificationsService: ServerNotificationsService
  private lateinit var projectServerNotificationsSubscriber: ProjectServerNotificationsSubscriber

  @Before
  fun setup() {
    serverNotificationsService = mock(ServerNotificationsService::class.java)
    projectServerNotificationsSubscriber = ProjectServerNotificationsSubscriber(project, serverNotificationsService, ImmediateExecutorService())
    clearNotifications()
  }

  @Test
  fun it_should_register_at_start_when_project_bound_and_server_supports_notifications() {
    connectProjectTo("host", "name", "projectKey")
    whenever(serverNotificationsService.isSupported(any())).thenReturn(true)

    projectServerNotificationsSubscriber.start()

    argumentCaptor<NotificationConfiguration>().apply {
      verify(serverNotificationsService).register(capture())
      assertThat(firstValue.projectKey()).isEqualTo("projectKey")
    }
  }

  @Test
  fun it_should_not_register_at_start_when_project_not_bound() {
    projectServerNotificationsSubscriber.start()

    verifyNoInteractions(serverNotificationsService)
  }

  @Test
  fun it_should_not_register_at_start_when_project_binding_is_invalid() {
    projectSettings.isBindingEnabled = true

    projectServerNotificationsSubscriber.start()

    verifyNoInteractions(serverNotificationsService)
  }

  @Test
  fun it_should_not_register_at_start_when_project_bound_but_server_does_not_support_notifications() {
    connectProjectTo("host", "name", "projectKey")
    whenever(serverNotificationsService.isSupported(any())).thenReturn(false)

    projectServerNotificationsSubscriber.start()

    verify(serverNotificationsService, never()).register(any())
  }

  @Test
  fun it_should_not_register_at_start_when_project_bound_but_notifications_disabled_in_connection() {
    connectProjectTo(ServerConnection.newBuilder().setName("name").setDisableNotifications(true).build(), "projectKey")

    projectServerNotificationsSubscriber.start()

    verifyNoInteractions(serverNotificationsService)
  }

  @Test
  fun it_should_register_when_global_settings_are_changed() {
    projectServerNotificationsSubscriber.start()
    connectProjectWithNotifications()

    project.messageBus.syncPublisher(GlobalConfigurationListener.TOPIC).applied(SonarLintGlobalSettings(), globalSettings)

    argumentCaptor<NotificationConfiguration>().apply {
      verify(serverNotificationsService, timeout(5000)).register(capture())
      assertThat(firstValue.projectKey()).isEqualTo("projectKey")
    }
  }

  @Test
  fun it_should_register_when_project_settings_are_changed() {
    projectServerNotificationsSubscriber.start()
    connectProjectWithNotifications()

    project.messageBus.syncPublisher(ProjectConfigurationListener.TOPIC).changed(projectSettings)

    argumentCaptor<NotificationConfiguration>().apply {
      verify(serverNotificationsService, timeout(5000)).register(capture())
      assertThat(firstValue.projectKey()).isEqualTo("projectKey")
    }
  }

  @Test
  fun it_should_unregister_first_when_registering_again() {
    connectProjectWithNotifications()
    projectServerNotificationsSubscriber.start()

    project.messageBus.syncPublisher(GlobalConfigurationListener.TOPIC).applied(SonarLintGlobalSettings(), globalSettings)

    verify(serverNotificationsService, timeout(5000)).unregister(any())
  }

  @Test
  fun it_should_unregister_when_disposed() {
    connectProjectTo("host", "name", "projectKey")
    whenever(serverNotificationsService.isSupported(any())).thenReturn(true)
    projectServerNotificationsSubscriber.start()

    projectServerNotificationsSubscriber.dispose()

    verify(serverNotificationsService).unregister(any())
  }

  @Test
  fun it_should_display_a_balloon_when_receiving_a_notification() {
    connectProjectWithNotifications()
    projectServerNotificationsSubscriber.start()
    argumentCaptor<NotificationConfiguration>().apply {
      verify(serverNotificationsService).register(capture())
      val listener = firstValue.listener()

      listener.handle(aServerNotification("category", "message", "link", "projectKey"))
    }

    assertThat(projectNotifications)
      .extracting("title", "content")
      .containsExactly(tuple("<b>SonarQube Notification</b>", "message"))
  }

  private fun connectProjectWithNotifications() {
    connectProjectTo("host", "name", "projectKey")
    whenever(serverNotificationsService.isSupported(any())).thenReturn(true)
  }
}
