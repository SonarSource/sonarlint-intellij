/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
package org.sonarlint.intellij.core

import com.intellij.openapi.project.ProjectManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.any
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.wizard.ServerConnectionCreator
import org.sonarlint.intellij.eq
import org.sonarlint.intellij.ui.ModalPresenter
import org.sonarlint.intellij.ui.ProjectSelectionDialog

class ProjectBindingAssistantTest : AbstractSonarLintLightTests() {

    private lateinit var projectManager: ProjectManager
    private lateinit var connectionCreator: ServerConnectionCreator
    private lateinit var modalPresenter: ModalPresenter
    private lateinit var projectSelectionDialog: ProjectSelectionDialog
    private lateinit var bindingManager: ProjectBindingManager
    private lateinit var assistant: ProjectBindingAssistant

    @Before
    fun setup() {
        modalPresenter = mock(ModalPresenter::class.java)
        projectManager = mock(ProjectManager::class.java)
        connectionCreator = mock(ServerConnectionCreator::class.java)
        projectSelectionDialog = mock(ProjectSelectionDialog::class.java)
        bindingManager = mock(ProjectBindingManager::class.java)
        assistant = ProjectBindingAssistant("title",
                projectManager,
                connectionCreator,
                modalPresenter,
                {projectSelectionDialog})

        `when`(projectManager.openProjects).thenReturn(emptyArray())
        globalSettings.serverConnections = emptyList()
        replaceProjectService(ProjectBindingManager::class.java, bindingManager)
    }

    @Test
    fun `it should request connection creation if not exist`() {
        assistant.bind("projectKey", "serverUrl")

        verify(modalPresenter).showConfirmModal("title", "No connections configured to 'serverUrl'.", "Create connection")
    }

    @Test
    fun `it should not request connection creation if exist`() {
        globalSettings.addServerConnection(ServerConnection.newBuilder().setHostUrl("serverUrl").build())

        assistant.bind("projectKey", "serverUrl")

        verify(modalPresenter, times(0)).showConfirmModal("title", "No connections configured to 'serverUrl'.", "Create connection")
    }

    @Test
    fun `it should select project after connection wizard is completed`() {
        `when`(modalPresenter.showConfirmModal("title", "No connections configured to 'serverUrl'.", "Create connection")).thenReturn(true)
        `when`(connectionCreator.createThroughWizard("serverUrl")).thenReturn(ServerConnection.newBuilder().setHostUrl("serverUrl").build())

        assistant.bind("projectKey", "serverUrl")

        verify(modalPresenter).showConfirmModal("title", "Cannot automatically find a project bound to:\n" +
        "  • Project: projectKey\n" +
                "  • Server: serverUrl\n" +
                "Please manually select a project.", "Select project")
    }

    @Test
    fun `it should not return bound project if connection creation is not confirmed`() {
        `when`(modalPresenter.showConfirmModal("title", "No connections configured to 'serverUrl'.", "Create connection")).thenReturn(false)

        val boundProject = assistant.bind("projectKey", "serverUrl")

        assertThat(boundProject).isNull()
    }

    @Test
    fun `it should not return bound project if connection creation wizard is canceled`() {
        `when`(modalPresenter.showConfirmModal("title", "No connections configured to 'serverUrl'.", "Create connection")).thenReturn(true)
        `when`(connectionCreator.createThroughWizard("serverUrl")).thenReturn(null)

        val boundProject = assistant.bind("projectKey", "serverUrl")

        assertThat(boundProject).isNull()
    }

    @Test
    fun `it should return an open bound project if it matches project key and server url`() {
        val connection = ServerConnection.newBuilder().setHostUrl("serverUrl").setName("connectionName").build()
        globalSettings.addServerConnection(connection)
        projectSettings.bindTo(connection, "projectKey")
        `when`(projectManager.openProjects).thenReturn(arrayOf(project))

        val boundProject = assistant.bind("projectKey", "serverUrl")

        assertThat(boundProject).isEqualTo(BoundProject(project, connection))
    }

    @Test
    fun `it should return a selected bound project`() {
        val connection = ServerConnection.newBuilder().setHostUrl("serverUrl").setName("connectionName").build()
        globalSettings.addServerConnection(connection)
        projectSettings.bindTo(connection, "projectKey")
        `when`(modalPresenter.showConfirmModal(eq("title"), any(), eq("Select project"))).thenReturn(true)
        `when`(projectSelectionDialog.selectProject()).thenReturn(project)

        val boundProject = assistant.bind("projectKey", "serverUrl")

        assertThat(boundProject).isEqualTo(BoundProject(project, connection))
    }

    @Test
    fun `it should return null when project selection is canceled`() {
        val connection = ServerConnection.newBuilder().setHostUrl("serverUrl").setName("connectionName").build()
        globalSettings.addServerConnection(connection)
        projectSettings.bindTo(connection, "projectKey")
        `when`(modalPresenter.showConfirmModal(eq("title"), any(), eq("Select project"))).thenReturn(false)

        val boundProject = assistant.bind("projectKey", "serverUrl")

        assertThat(boundProject).isNull()
    }

    @Test
    fun `it should return null when project selection dialog is canceled`() {
        val connection = ServerConnection.newBuilder().setHostUrl("serverUrl").setName("connectionName").build()
        globalSettings.addServerConnection(connection)
        projectSettings.bindTo(connection, "projectKey")
        `when`(modalPresenter.showConfirmModal(eq("title"), any(), eq("Select project"))).thenReturn(true)
        `when`(projectSelectionDialog.selectProject()).thenReturn(null)

        val boundProject = assistant.bind("projectKey", "serverUrl")

        assertThat(boundProject).isNull()
    }

    @Test
    fun `it should bind an unbound selected project`() {
        val connection = ServerConnection.newBuilder().setHostUrl("serverUrl").setName("connectionName").build()
        globalSettings.addServerConnection(connection)
        `when`(modalPresenter.showConfirmModal(eq("title"), any(), eq("Select project"))).thenReturn(true)
        `when`(projectSelectionDialog.selectProject()).thenReturn(project)
        `when`(modalPresenter.showConfirmModal("title", "You are going to bind current project to 'serverUrl'. Do you agree?", "Yes")).thenReturn(true)

        val boundProject = assistant.bind("projectKey", "serverUrl")

        assertThat(boundProject).isEqualTo(BoundProject(project, connection))
        verify(bindingManager).bindTo(connection, "projectKey", emptyMap())
    }

    @Test
    fun `it should return null when automatic binding of selected project is canceled`() {
        val connection = ServerConnection.newBuilder().setHostUrl("serverUrl").setName("connectionName").build()
        globalSettings.addServerConnection(connection)
        `when`(modalPresenter.showConfirmModal(eq("title"), any(), eq("Select project"))).thenReturn(true)
        `when`(projectSelectionDialog.selectProject()).thenReturn(project)
        `when`(modalPresenter.showConfirmModal("title", "You are going to bind current project to 'serverUrl'. Do you agree?", "Yes")).thenReturn(false)

        val boundProject = assistant.bind("projectKey", "serverUrl")

        assertThat(boundProject).isNull()
    }
}
