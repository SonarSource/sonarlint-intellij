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
package org.sonarlint.intellij.finding.issue.risks

import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Test
import org.sonar.api.issue.impact.SoftwareQuality
import org.sonarsource.sonarlint.core.client.utils.ImpactSeverity
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.ImpactDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto.Severity
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto.Status
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto.Transition
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto.Type

class LocalDependencyRiskTest {

    @Test
    fun `should map to dto`() {
        val id = UUID.randomUUID()
        val dto = DependencyRiskDto(
            id, Type.VULNERABILITY, Severity.HIGH, Status.OPEN,
            "com.test.package", "1.2.3",
            listOf(Transition.CONFIRM, Transition.ACCEPT, Transition.SAFE)
        )

        val tested = LocalDependencyRisk(dto)

        assertThatCode { }.doesNotThrowAnyException()

        assertThat(tested).extracting(
            "id",
            "CleanCodeAttribute",
            "Impacts",
            "HighestQuality",
            "HighestImpact",
            "ServerKey",
            "RuleKey",
            "Type",
            "RuleDescriptionContextKey",
            "Valid",
            "OnNewCode",
            "Resolved",
            "AiCodeFixable",
        ).containsExactly(
            tuple(
                id,
                null,
                emptyList<ImpactDto>(),
                SoftwareQuality.SECURITY,
                ImpactSeverity.HIGH,
                null,
                "com.test.package 1.2.3",
                null, // todo no RuleType?
                null,
                null,
                true,
                false,
                false,
                false
            )
        )
    }
}
