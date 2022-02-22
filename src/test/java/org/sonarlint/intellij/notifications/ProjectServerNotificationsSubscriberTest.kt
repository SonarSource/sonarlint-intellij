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

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.junit.MockitoJUnitRunner
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.analysis.JavaAnalysisConfiguratorTests
import org.sonarlint.intellij.any
import org.sonarlint.intellij.capture
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.ServerNotificationsService
import org.sonarlint.intellij.messages.GlobalConfigurationListener
import org.sonarlint.intellij.messages.ProjectConfigurationListener
import org.sonarsource.sonarlint.core.client.api.common.NotificationConfiguration
import java.io.IOException

@RunWith(MockitoJUnitRunner::class)
class ProjectServerNotificationsSubscriberTest : AbstractSonarLintLightTests() {
  private lateinit var serverNotificationsService: ServerNotificationsService
  private lateinit var projectServerNotificationsSubscriber: ProjectServerNotificationsSubscriber

  @Captor
  private lateinit var notificationConfigurationCaptor: ArgumentCaptor<NotificationConfiguration>

  @Before
  fun setup() {
    serverNotificationsService = mock(ServerNotificationsService::class.java)
    projectServerNotificationsSubscriber = ProjectServerNotificationsSubscriber(project, serverNotificationsService)
    clearNotifications()
  }

  @Test
  fun it_should_register_at_start_when_project_bound_and_server_supports_notifications() {
    connectProjectTo("host", "name", "projectKey")
    `when`(serverNotificationsService.isSupported(any())).thenReturn(true)

    projectServerNotificationsSubscriber.start()

    verify(serverNotificationsService).register(capture(notificationConfigurationCaptor))
    val notificationConfiguration = notificationConfigurationCaptor.value
    assertThat(notificationConfiguration.projectKey()).isEqualTo("projectKey")
  }

  @Test
  fun it_should_not_register_at_start_when_project_not_bound() {
    projectServerNotificationsSubscriber.start()

    verifyZeroInteractions(serverNotificationsService)
  }

  @Test
  fun it_should_not_register_at_start_when_project_binding_is_invalid() {
    projectSettings.isBindingEnabled = true

    projectServerNotificationsSubscriber.start()

    verifyZeroInteractions(serverNotificationsService)
  }

  @Test
  fun it_should_not_register_at_start_when_project_bound_but_server_does_not_support_notifications() {
    connectProjectTo("host", "name", "projectKey")
    `when`(serverNotificationsService.isSupported(any())).thenReturn(false)

    projectServerNotificationsSubscriber.start()

    verify(serverNotificationsService, never()).register(any())
  }

  @Test
  fun it_should_not_register_at_start_when_project_bound_but_notifications_disabled_in_connection() {
    connectProjectTo(ServerConnection.newBuilder().setName("name").setDisableNotifications(true).build(), "projectKey")

    projectServerNotificationsSubscriber.start()

    verifyZeroInteractions(serverNotificationsService)
  }

  @Test
  fun it_should_register_when_global_settings_are_changed() {
    projectServerNotificationsSubscriber.start()
    connectProjectWithNotifications()

    project.messageBus.syncPublisher(GlobalConfigurationListener.TOPIC).applied(globalSettings)

    verify(serverNotificationsService, timeout(5000)).register(capture(notificationConfigurationCaptor))
    val notificationConfiguration = notificationConfigurationCaptor.value
    assertThat(notificationConfiguration.projectKey()).isEqualTo("projectKey")
  }

  @Test
  fun it_should_register_when_project_settings_are_changed() {
    projectServerNotificationsSubscriber.start()
    connectProjectWithNotifications()

    project.messageBus.syncPublisher(ProjectConfigurationListener.TOPIC).changed(projectSettings)

    verify(serverNotificationsService, timeout(5000)).register(capture(notificationConfigurationCaptor))
    val notificationConfiguration = notificationConfigurationCaptor.value
    assertThat(notificationConfiguration.projectKey()).isEqualTo("projectKey")
  }

  @Test
  fun it_should_unregister_first_when_registering_again() {
    connectProjectWithNotifications()
    projectServerNotificationsSubscriber.start()

    project.messageBus.syncPublisher(GlobalConfigurationListener.TOPIC).applied(globalSettings)

    verify(serverNotificationsService, timeout(5000)).unregister(any())
  }

  @Test
  fun it_should_unregister_when_disposed() {
    connectProjectTo("host", "name", "projectKey")
    `when`(serverNotificationsService.isSupported(any())).thenReturn(true)
    projectServerNotificationsSubscriber.start()

    projectServerNotificationsSubscriber.dispose()

    verify(serverNotificationsService).unregister(any())
  }

  @Test
  fun it_should_display_a_balloon_when_receiving_a_notification() {
    connectProjectWithNotifications()
    projectServerNotificationsSubscriber.start()
    verify(serverNotificationsService).register(capture(notificationConfigurationCaptor))
    val listener = notificationConfigurationCaptor.value.listener()

    listener.handle(aServerNotification("category", "message", "link", "projectKey"))

    assertThat(projectNotifications)
      .extracting("title", "content")
      .containsExactly(tuple("<b>SonarQube Notification</b>", "message"))
  }

  private fun connectProjectWithNotifications() {
    connectProjectTo("host", "name", "projectKey")
    `when`(serverNotificationsService.isSupported(any())).thenReturn(true)
  }
}
