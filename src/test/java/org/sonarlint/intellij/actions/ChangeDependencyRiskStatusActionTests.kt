/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testFramework.replaceService
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.finding.sca.aDependencyRisk
import org.sonarlint.intellij.notifications.SonarLintProjectNotifications
import org.sonarlint.intellij.util.DataKeys.Companion.DEPENDENCY_RISK_DATA_KEY
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto

class ChangeDependencyRiskStatusActionTests : AbstractSonarLintLightTests() {

    private lateinit var action: ChangeDependencyRiskStatusAction
    private lateinit var mockEvent: AnActionEvent
    private lateinit var mockProjectBindingManager: ProjectBindingManager
    private lateinit var mockServerConnection: ServerConnection
    private lateinit var mockNotifications: SonarLintProjectNotifications

    @BeforeEach
    fun initialization() {
        action = ChangeDependencyRiskStatusAction()
        mockEvent = mock(AnActionEvent::class.java)
        mockProjectBindingManager = mock(ProjectBindingManager::class.java)
        mockServerConnection = mock(ServerConnection::class.java)
        mockNotifications = mock(SonarLintProjectNotifications::class.java)

        `when`(mockEvent.project).thenReturn(project)
        `when`(mockServerConnection.productName).thenReturn("SonarQube Server")
        
        project.replaceService(ProjectBindingManager::class.java, mockProjectBindingManager, testRootDisposable)
        project.replaceService(SonarLintProjectNotifications::class.java, mockNotifications, testRootDisposable)
    }

    @Test
    fun `canChangeStatus should return false when dependency risk is already resolved`() {
        val resolvedRisk = aDependencyRisk(DependencyRiskDto.Status.SAFE)
        `when`(mockProjectBindingManager.tryGetServerConnection()).thenReturn(Optional.of(mockServerConnection))

        val canChange = ChangeDependencyRiskStatusAction.canChangeStatus(project, resolvedRisk)

        assertThat(canChange).isFalse()
    }

    @Test
    fun `canChangeStatus should return false when no server connection`() {
        val openRisk = aDependencyRisk(DependencyRiskDto.Status.OPEN)
        `when`(mockProjectBindingManager.tryGetServerConnection()).thenReturn(Optional.empty())

        val canChange = ChangeDependencyRiskStatusAction.canChangeStatus(project, openRisk)

        assertThat(canChange).isFalse()
    }

    @Test
    fun `canChangeStatus should return false when no transitions available`() {
        val riskWithoutTransitions = aDependencyRisk(emptyList())
        `when`(mockProjectBindingManager.tryGetServerConnection()).thenReturn(Optional.of(mockServerConnection))

        val canChange = ChangeDependencyRiskStatusAction.canChangeStatus(project, riskWithoutTransitions)

        assertThat(canChange).isFalse()
    }

    @Test
    fun `actionPerformed should show error when no dependency risk found`() {
        `when`(mockEvent.getData(DEPENDENCY_RISK_DATA_KEY)).thenReturn(null)

        action.actionPerformed(mockEvent)

        verify(mockNotifications).displayErrorNotification(any(), any(), any())
    }

    @Test
    fun `actionPerformed should not process when project is null`() {
        `when`(mockEvent.project).thenReturn(null)

        action.actionPerformed(mockEvent)

        verify(mockNotifications, never()).displayErrorNotification(any(), any(), any())
    }

} 
