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
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language

class SupportedLanguagesPanelTests {

    @Test
    fun `AnalyzerStatus PREMIUM has tooltip`() {
        assertThat(AnalyzerStatus.PREMIUM.tooltip).isEqualTo("Requires Connected Mode")
    }

    @Test
    fun `statuses without tooltip have null tooltip`() {
        val withoutTooltip = listOf(
            AnalyzerStatus.ACTIVE,
            AnalyzerStatus.SYNCED,
            AnalyzerStatus.DOWNLOADING,
            AnalyzerStatus.FAILED,
            AnalyzerStatus.UNSUPPORTED,
        )
        withoutTooltip.forEach { assertThat(it.tooltip).isNull() }
    }

    @Test
    fun `AnalyzerSource labels are non-blank`() {
        AnalyzerSource.values().forEach { assertThat(it.label).isNotBlank() }
    }

    @Test
    fun `SupportedLanguageRow holds expected values`() {
        val row = SupportedLanguageRow(
            language = Language.JAVA,
            displayName = "Java",
            status = AnalyzerStatus.ACTIVE,
            source = AnalyzerSource.LOCAL,
            version = "7.30.1",
            localVersion = "7.30.1",
        )

        assertThat(row.language).isEqualTo(Language.JAVA)
        assertThat(row.displayName).isEqualTo("Java")
        assertThat(row.status).isEqualTo(AnalyzerStatus.ACTIVE)
        assertThat(row.source).isEqualTo(AnalyzerSource.LOCAL)
        assertThat(row.version).isEqualTo("7.30.1")
        assertThat(row.localVersion).isEqualTo("7.30.1")
    }

    // -------------------------------------------------------------------------
    // SupportedLanguagesTableModel
    // -------------------------------------------------------------------------

    @Test
    fun `table model reports correct row and column count`() {
        val rows = listOf(
            SupportedLanguageRow(Language.JAVA, "Java", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "7.30.1", localVersion = "7.30.1"),
            SupportedLanguageRow(Language.PYTHON, "Python", AnalyzerStatus.PREMIUM, AnalyzerSource.LOCAL, null),
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
        val row = SupportedLanguageRow(Language.JAVA, "Java", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "7.30.1", localVersion = "7.30.1")
        val model = SupportedLanguagesTableModel(listOf(row))

        assertThat(model.getValueAt(0, SupportedLanguagesTableModel.Column.ANALYSIS_TYPE.ordinal)).isEqualTo(row)
    }

    @Test
    fun `table model getValueAt returns typed values for STATUS and SOURCE columns`() {
        val row = SupportedLanguageRow(Language.JAVA, "Java", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "7.30.1")
        val model = SupportedLanguagesTableModel(listOf(row))

        assertThat(model.getValueAt(0, SupportedLanguagesTableModel.Column.STATUS.ordinal)).isEqualTo(AnalyzerStatus.ACTIVE)
        assertThat(model.getValueAt(0, SupportedLanguagesTableModel.Column.SOURCE.ordinal)).isEqualTo(AnalyzerSource.LOCAL)
    }

    @Test
    fun `table model column classes are correct`() {
        val model = SupportedLanguagesTableModel(emptyList())

        assertThat(model.getColumnClass(SupportedLanguagesTableModel.Column.ANALYSIS_TYPE.ordinal)).isEqualTo(SupportedLanguageRow::class.java)
        assertThat(model.getColumnClass(SupportedLanguagesTableModel.Column.STATUS.ordinal)).isEqualTo(AnalyzerStatus::class.java)
        assertThat(model.getColumnClass(SupportedLanguagesTableModel.Column.SOURCE.ordinal)).isEqualTo(AnalyzerSource::class.java)
    }

    @Test
    fun `table model cells are not editable`() {
        val row = SupportedLanguageRow(Language.JAVA, "Java", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "7.30.1")
        val model = SupportedLanguagesTableModel(listOf(row))

        for (col in 0 until model.columnCount) {
            assertThat(model.isCellEditable(0, col)).isFalse()
        }
    }

    @Test
    fun `table model getRow returns correct row`() {
        val row = SupportedLanguageRow(Language.JAVA, "Java", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "7.30.1")
        val model = SupportedLanguagesTableModel(listOf(row))

        assertThat(model.getRow(0)).isEqualTo(row)
    }

}

