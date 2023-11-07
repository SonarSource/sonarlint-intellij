/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
package org.sonarlint.intellij.finding

import java.nio.file.Paths
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.finding.ShowFinding.Companion.handleFlows
import org.sonarsource.sonarlint.core.rpc.protocol.common.FlowDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.LocationDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.TextRangeDto

class ShowFindingTests : AbstractSonarLintLightTests() {

    @Test
    fun should_transform_location() {
        val file = myFixture.configureByText("file.ext", "Text")
        val listFlowDto = listOf(
            FlowDto(
                listOf(
                    LocationDto(TextRangeDto(1, 0, 1, 4), "msg", Paths.get("file.ext"), "Text"),
                    LocationDto(TextRangeDto(1, 0, 1, 4), "msg", Paths.get("file.ext"), null),
                    LocationDto(TextRangeDto(1, 0, 1, 4), "msg", Paths.get("unknown"), null)
                )
            )
        )

        val flowsResult = handleFlows(project, listFlowDto)

        assertThat(flowsResult[0].locations).extracting(
            { it.file },
            { it.message },
            { it.originalFileName },
            { it.range?.startOffset },
            { it.range?.endOffset },
            { it.range?.document?.text },
            { it.textRangeHash },
        ).containsExactly(
            tuple(file.virtualFile, "msg", null, null, null, null, null),
            tuple(file.virtualFile, "msg", null, 0, 4, "Text", "9dffbf69ffba8bc38bc4e01abf4b1675")
        )
    }

}
