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
package org.sonarlint.intellij.config.project

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.ArtifactSourceDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStateDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStatusDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language

class PluginStatusMapperTests {

    @Test
    fun `maps known plugin status to supported language row`() {
        val dto = PluginStatusDto("Java", PluginStateDto.ACTIVE, ArtifactSourceDto.EMBEDDED, "7.30.1", "7.30.1")

        val rows = PluginStatusMapper.mapToRows(listOf(dto))

        assertThat(rows).hasSize(1)
        assertThat(rows[0]).isEqualTo(
            SupportedLanguageRow(
                language = Language.JAVA,
                displayName = "Java",
                status = AnalyzerStatus.ACTIVE,
                source = AnalyzerSource.LOCAL,
                version = "7.30.1",
                localVersion = "7.30.1",
            )
        )
    }

    @Test
    fun `maps synced source from SonarQube Cloud`() {
        val dto = PluginStatusDto("Python", PluginStateDto.SYNCED, ArtifactSourceDto.SONARQUBE_CLOUD, "4.23.0", "4.22.0")

        val row = PluginStatusMapper.mapToRows(listOf(dto)).single()

        assertThat(row.status).isEqualTo(AnalyzerStatus.SYNCED)
        assertThat(row.source).isEqualTo(AnalyzerSource.SONARQUBE_CLOUD)
        assertThat(row.isVersionOverriddenByServer).isTrue()
    }

    @Test
    fun `maps null source as local`() {
        val dto = PluginStatusDto("Scala", PluginStateDto.PREMIUM, null, null, null)

        val row = PluginStatusMapper.mapToRows(listOf(dto)).single()

        assertThat(row.source).isEqualTo(AnalyzerSource.LOCAL)
        assertThat(row.status).isEqualTo(AnalyzerStatus.PREMIUM)
    }

    @Test
    fun `ignores unknown plugin names`() {
        val known = PluginStatusDto("Java", PluginStateDto.ACTIVE, ArtifactSourceDto.EMBEDDED, "7.30.1", "7.30.1")
        val unknown = PluginStatusDto("Unknown Language", PluginStateDto.ACTIVE, ArtifactSourceDto.EMBEDDED, "1.0", "1.0")

        val rows = PluginStatusMapper.mapToRows(listOf(known, unknown))

        assertThat(rows).hasSize(1)
        assertThat(rows.single().displayName).isEqualTo("Java")
    }
}
