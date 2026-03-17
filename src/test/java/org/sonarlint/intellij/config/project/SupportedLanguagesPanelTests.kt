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

import com.intellij.openapi.project.Project
import java.awt.Container
import javax.swing.JButton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language

class SupportedLanguagesPanelTests {

    private fun panel(onSetupConnectedMode: () -> Unit = {}) = SupportedLanguagesPanel(mock(Project::class.java), onSetupConnectedMode)

    // -------------------------------------------------------------------------
    // Panel contract
    // -------------------------------------------------------------------------

    @Test
    fun `panel is never modified`() {
        val p = panel()
        assertThat(p.isModified(SonarLintProjectSettings())).isFalse()
    }

    @Test
    fun `save is a no-op`() {
        val settings = SonarLintProjectSettings()
        panel().save(settings)
        assertThat(settings.isBindingEnabled).isFalse()
        assertThat(settings.additionalProperties).isEmpty()
    }

    @Test
    fun `component is not null`() {
        assertThat(panel().component).isNotNull()
    }

    // -------------------------------------------------------------------------
    // Setup Connected Mode callback
    // -------------------------------------------------------------------------

    @Test
    fun `setup connected mode callback is invoked when clicking setup link`() {
        var invoked = false
        val p = panel { invoked = true }
        val setupButton = findButtonByText(p.component, "Set up connection")
        assertThat(setupButton).isNotNull()
        setupButton!!.doClick()
        assertThat(invoked).isTrue()
    }

    private fun findButtonByText(root: Container, text: String): JButton? {
        root.components.forEach { component ->
            if (component is JButton && component.text.contains(text)) {
                return component
            }
            if (component is Container) {
                findButtonByText(component, text)?.let { return it }
            }
        }
        return null
    }

    // -------------------------------------------------------------------------
    // AnalyzerStatus
    // -------------------------------------------------------------------------

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
    fun `AnalyzerStatus labels are non-blank`() {
        AnalyzerStatus.values().forEach { assertThat(it.label).isNotBlank() }
    }

    // -------------------------------------------------------------------------
    // AnalyzerSource
    // -------------------------------------------------------------------------

    @Test
    fun `AnalyzerSource labels are non-blank`() {
        AnalyzerSource.values().forEach { assertThat(it.label).isNotBlank() }
    }

    // -------------------------------------------------------------------------
    // SupportedLanguageRow
    // -------------------------------------------------------------------------

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

    @Test
    fun `version is not overridden when version equals localVersion`() {
        val row = SupportedLanguageRow(Language.JAVA, "Java", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "7.30.1", localVersion = "7.30.1")
        assertThat(row.isVersionOverriddenByServer).isFalse()
    }

    @Test
    fun `version is overridden when server version differs from localVersion`() {
        val row = SupportedLanguageRow(Language.JAVA, "Java", AnalyzerStatus.SYNCED, AnalyzerSource.SONARQUBE_SERVER, "7.31.0", localVersion = "7.30.1")
        assertThat(row.isVersionOverriddenByServer).isTrue()
    }

    @Test
    fun `version is not overridden when localVersion is null`() {
        val row = SupportedLanguageRow(Language.SCALA, "Scala", AnalyzerStatus.PREMIUM, AnalyzerSource.LOCAL, null, localVersion = null)
        assertThat(row.isVersionOverriddenByServer).isFalse()
    }

    @Test
    fun `version is not overridden when version is null`() {
        val row = SupportedLanguageRow(Language.SCALA, "Scala", AnalyzerStatus.PREMIUM, AnalyzerSource.LOCAL, null, localVersion = "1.0.0")
        assertThat(row.isVersionOverriddenByServer).isFalse()
    }

    @Test
    fun `SupportedLanguageRow localVersion defaults to null`() {
        val row = SupportedLanguageRow(Language.SCALA, "Scala", AnalyzerStatus.PREMIUM, AnalyzerSource.LOCAL, null)
        assertThat(row.localVersion).isNull()
    }

    @Test
    fun `premium row has null version`() {
        val row = SupportedLanguageRow(Language.SCALA, "Scala", AnalyzerStatus.PREMIUM, AnalyzerSource.LOCAL, null)
        assertThat(row.version).isNull()
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
    fun `table model getValueAt returns full row for ANALYSIS_TYPE and VERSION columns`() {
        val row = SupportedLanguageRow(Language.JAVA, "Java", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "7.30.1", localVersion = "7.30.1")
        val model = SupportedLanguagesTableModel(listOf(row))

        assertThat(model.getValueAt(0, SupportedLanguagesTableModel.Column.ANALYSIS_TYPE.ordinal)).isEqualTo(row)
        assertThat(model.getValueAt(0, SupportedLanguagesTableModel.Column.VERSION.ordinal)).isEqualTo(row)
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
        assertThat(model.getColumnClass(SupportedLanguagesTableModel.Column.VERSION.ordinal)).isEqualTo(SupportedLanguageRow::class.java)
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

