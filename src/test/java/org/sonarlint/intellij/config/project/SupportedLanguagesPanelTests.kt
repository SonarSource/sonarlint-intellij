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
import org.sonarlint.intellij.config.project.supported.languages.SupportedLanguagesTableModel
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.ArtifactSourceDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStateDto
import org.sonarsource.sonarlint.core.rpc.protocol.backend.plugin.PluginStatusDto
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language

class SupportedLanguagesPanelTests {

    @Test
    fun `ArtifactSourceDto labels are non-blank`() {
        ArtifactSourceDto.values().forEach { assertThat(it.label).isNotBlank() }
    }

    // -------------------------------------------------------------------------
    // SupportedLanguagesTableModel
    // -------------------------------------------------------------------------

    @Test
    fun `table model reports correct row and column count`() {
        val rows = listOf(
            PluginStatusDto(Language.JAVA, "Java", PluginStateDto.ACTIVE, ArtifactSourceDto.EMBEDDED, "7.30.1", "7.30.1", null),
            PluginStatusDto(Language.PYTHON, "Python", PluginStateDto.PREMIUM, ArtifactSourceDto.EMBEDDED, null, null, null),
        )
        val model = SupportedLanguagesTableModel(rows)

        assertThat(model.rowCount).isEqualTo(2)
        assertThat(model.columnCount).isEqualTo(SupportedLanguagesTableModel.Column.values().size)
    }

    @Test
    fun `table model column names match enum headers`() {
        val model = SupportedLanguagesTableModel(emptyList())

        SupportedLanguagesTableModel.Column.values().forEachIndexed { index, column ->
            assertThat(model.getColumnName(index)).isEqualTo(column.header)
        }
    }

    @Test
    fun `table model getValueAt returns full row for ANALYSIS_TYPE column`() {
        val row = PluginStatusDto(Language.JAVA, "Java", PluginStateDto.ACTIVE, ArtifactSourceDto.EMBEDDED, "7.30.1", "7.30.1", null)
        val model = SupportedLanguagesTableModel(listOf(row))

        assertThat(model.getValueAt(0, SupportedLanguagesTableModel.Column.ANALYSIS_TYPE.ordinal)).isEqualTo(row)
    }

    @Test
    fun `table model getValueAt returns typed values for STATUS and SOURCE columns`() {
        val row = PluginStatusDto(Language.JAVA, "Java", PluginStateDto.ACTIVE, ArtifactSourceDto.EMBEDDED, "7.30.1", null, null)
        val model = SupportedLanguagesTableModel(listOf(row))

        assertThat(model.getValueAt(0, SupportedLanguagesTableModel.Column.STATUS.ordinal)).isEqualTo(PluginStateDto.ACTIVE)
        assertThat(model.getValueAt(0, SupportedLanguagesTableModel.Column.SOURCE.ordinal)).isEqualTo(ArtifactSourceDto.EMBEDDED)
    }

    @Test
    fun `table model column classes are correct`() {
        val model = SupportedLanguagesTableModel(emptyList())

        assertThat(model.getColumnClass(SupportedLanguagesTableModel.Column.ANALYSIS_TYPE.ordinal)).isEqualTo(PluginStatusDto::class.java)
        assertThat(model.getColumnClass(SupportedLanguagesTableModel.Column.STATUS.ordinal)).isEqualTo(PluginStateDto::class.java)
        assertThat(model.getColumnClass(SupportedLanguagesTableModel.Column.SOURCE.ordinal)).isEqualTo(ArtifactSourceDto::class.java)
    }

    @Test
    fun `table model cells are not editable`() {
        val row = PluginStatusDto(Language.JAVA, "Java", PluginStateDto.ACTIVE, ArtifactSourceDto.EMBEDDED, "7.30.1", null, null)
        val model = SupportedLanguagesTableModel(listOf(row))

        for (col in 0 until model.columnCount) {
            assertThat(model.isCellEditable(0, col)).isFalse()
        }
    }

    @Test
    fun `table model getRow returns correct row`() {
        val row = PluginStatusDto(Language.JAVA, "Java", PluginStateDto.ACTIVE, ArtifactSourceDto.EMBEDDED, "7.30.1", null, null)
        val model = SupportedLanguagesTableModel(listOf(row))

        assertThat(model.getRow(0)).isEqualTo(row)
    }

}

