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
package org.sonarlint.intellij.config.project

import java.awt.Container
import java.util.concurrent.CompletableFuture
import javax.swing.JButton
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.config.project.supported.languages.SupportedLanguagesPanel
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.telemetry.SonarLintTelemetry
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.GetPluginStatusesResponse

class SupportedLanguagesPanelTelemetryTests : AbstractSonarLintLightTests() {

    private val telemetry: SonarLintTelemetry = mock()
    private val backendService: BackendService = mock()

    @BeforeEach
    fun setup() {
        replaceApplicationService(SonarLintTelemetry::class.java, telemetry)
        replaceApplicationService(BackendService::class.java, backendService)
        whenever(backendService.getPluginStatuses(project))
            .thenReturn(CompletableFuture.completedFuture(GetPluginStatusesResponse(emptyList())))
    }

    @Test
    fun `panel opened telemetry is sent on load`() {
        val panel = SupportedLanguagesPanel(project) {}

        panel.load(projectSettings)

        verify(telemetry, timeout(1000)).supportedLanguagesPanelOpened()
    }

    @Test
    fun `CTA clicked telemetry is sent when setup button is clicked`() {
        val panel = SupportedLanguagesPanel(project) {}
        val setupButton = findButton(panel.component, "Set up connection")

        setupButton.doClick()

        verify(telemetry, timeout(1000)).supportedLanguagesPanelCtaClicked()
    }

    private fun findButton(root: Container, text: String): JButton {
        for (component in root.components) {
            if (component is JButton && component.text == text) return component
            if (component is Container) {
                try {
                    return findButton(component, text)
                } catch (_: NoSuchElementException) {
                    // continue searching
                }
            }
        }
        throw NoSuchElementException("Button '$text' not found")
    }

}
