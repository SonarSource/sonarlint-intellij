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
package org.sonarlint.intellij.ui.risks.tree

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.finding.sca.aDependencyRisk
import org.sonarsource.sonarlint.core.rpc.protocol.backend.tracking.DependencyRiskDto

class DependencyRiskResolvedFilterTests {

    @Test
    fun `OPEN_ONLY should filter out resolved risks`() {
        val unresolvedOpen = aDependencyRisk(DependencyRiskDto.Status.OPEN)
        val unresolvedConfirm = aDependencyRisk(DependencyRiskDto.Status.CONFIRM)
        val resolvedSafe = aDependencyRisk(DependencyRiskDto.Status.SAFE)

        assertThat(DependencyRiskResolvedFilter.OPEN_ONLY.filter(unresolvedOpen)).isTrue()
        assertThat(DependencyRiskResolvedFilter.OPEN_ONLY.filter(unresolvedConfirm)).isTrue()
        assertThat(DependencyRiskResolvedFilter.OPEN_ONLY.filter(resolvedSafe)).isFalse()
    }

    @Test
    fun `ALL should accept both resolved and unresolved risks`() {
        val unresolved = aDependencyRisk(DependencyRiskDto.Status.OPEN)
        val resolved = aDependencyRisk(DependencyRiskDto.Status.ACCEPT)

        assertThat(DependencyRiskResolvedFilter.ALL.filter(unresolved)).isTrue()
        assertThat(DependencyRiskResolvedFilter.ALL.filter(resolved)).isTrue()
    }

}


