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
package org.sonarlint.intellij.telemetry

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.core.BackendService
import org.sonarsource.sonarlint.core.rpc.protocol.backend.ai.AiIntegrationHost
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationAction
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationActionParams
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.AiIntegrationActionStatus

class SonarLintTelemetryTests : AbstractSonarLintLightTests() {
    @ParameterizedTest
    @EnumSource(AiIntegrationActionStatus::class)
    fun `reports CLI uninstall through backend telemetry with IntelliJ host and no agent`(status: AiIntegrationActionStatus) {
        val backend: BackendService = mock()
        val service: TelemetryRpcService = mock()
        replaceApplicationService(BackendService::class.java, backend)

        SonarLintTelemetry().aiIntegrationAction(AiIntegrationAction.UNINSTALL_CLI, status)

        val notification = argumentCaptor<(TelemetryRpcService) -> Unit>()
        verify(backend, timeout(1000)).notifyTelemetry(notification.capture())
        notification.firstValue(service)
        val params = argumentCaptor<AiIntegrationActionParams>()
        verify(service).aiIntegrationAction(params.capture())
        assertThat(params.firstValue.action).isEqualTo(AiIntegrationAction.UNINSTALL_CLI)
        assertThat(params.firstValue.status).isEqualTo(status)
        assertThat(params.firstValue.host).isEqualTo(AiIntegrationHost.INTELLIJ)
        assertThat(params.firstValue.agent).isNull()
    }
}
