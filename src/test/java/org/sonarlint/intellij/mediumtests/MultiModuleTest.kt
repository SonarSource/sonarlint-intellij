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
package org.sonarlint.intellij.mediumtests

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.sonarlint.intellij.AbstractSonarLintHeavyTest
import org.sonarlint.intellij.any
import org.sonarlint.intellij.capture
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.ModuleBindingManager
import org.sonarlint.intellij.core.ServerNotificationsService
import org.sonarlint.intellij.notifications.ProjectServerNotificationsSubscriber
import org.sonarlint.intellij.util.ImmediateExecutorService
import org.sonarsource.sonarlint.core.serverconnection.smartnotifications.NotificationConfiguration

class MultiModuleTest : AbstractSonarLintHeavyTest() {

    fun test_it_should_register_at_start_when_module_override_binding_and_server_supports_notifications() {
        val module = createModule("foo")

        val serverNotificationsService = Mockito.mock(ServerNotificationsService::class.java)
        val projectServerNotificationsSubscriber = ProjectServerNotificationsSubscriber(project, serverNotificationsService, ImmediateExecutorService())

        connectProjectTo(ServerConnection.newBuilder().setHostUrl("host").setName("mySq").build(), "projectKey1")
        connectModuleTo(module, "moduleProjectKey")

        Mockito.`when`(serverNotificationsService.isSupported(any())).thenReturn(true)

        projectServerNotificationsSubscriber.start()

        val notificationConfigurationCaptor = ArgumentCaptor.forClass(NotificationConfiguration::class.java)

        Mockito.verify(serverNotificationsService, Mockito.times(2)).register(capture(notificationConfigurationCaptor))
        val notificationConfigurations = notificationConfigurationCaptor.allValues
        assertThat(notificationConfigurations)
            .extracting(NotificationConfiguration::projectKey)
            .containsOnly(tuple("moduleProjectKey"), tuple("projectKey1"))
    }

    fun test_should_return_project_key_for_module_binding_override() {
        val secondModule = createModule("foo")

        connectProjectTo(ServerConnection.newBuilder().setName("server1").build(), "project1")
        connectModuleTo(secondModule, "project2")

        assertThat(SonarLintUtils.getService(module, ModuleBindingManager::class.java).resolveProjectKey()).isEqualTo("project1")
        assertThat(SonarLintUtils.getService(secondModule, ModuleBindingManager::class.java).resolveProjectKey()).isEqualTo("project2")
    }

    fun test_should_ignore_module_binding_if_only_one_module() {
        connectProjectTo(ServerConnection.newBuilder().setName("server1").build(), "project1")
        connectModuleTo("ignored")

        assertThat(SonarLintUtils.getService(module, ModuleBindingManager::class.java).resolveProjectKey()).isEqualTo("project1")
    }
}
