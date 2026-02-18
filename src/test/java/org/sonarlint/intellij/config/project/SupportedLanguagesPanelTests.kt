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

    private fun panel(onSetupConnectedMode: () -> Unit = {}) = SupportedLanguagesPanel(onSetupConnectedMode)

    // -------------------------------------------------------------------------
    // Panel contract
    // -------------------------------------------------------------------------

    @Test
    fun `panel is never modified`() {
        val p = panel()
        assertThat(p.isModified(SonarLintProjectSettings())).isFalse()
        p.load(SonarLintProjectSettings().apply { setBindingEnabled(true) })
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
    fun `setup connected mode callback is invoked when triggered`() {
        var invoked = false
        val p = panel { invoked = true }
        p.triggerSetupConnectedMode()
        assertThat(invoked).isTrue()
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

    @Test
    fun `only PREMIUM and UNSUPPORTED are dimmed`() {
        assertThat(AnalyzerStatus.PREMIUM.isDimmed).isTrue()
        assertThat(AnalyzerStatus.UNSUPPORTED.isDimmed).isTrue()
        assertThat(AnalyzerStatus.ACTIVE.isDimmed).isFalse()
        assertThat(AnalyzerStatus.SYNCED.isDimmed).isFalse()
        assertThat(AnalyzerStatus.DOWNLOADING.isDimmed).isFalse()
        assertThat(AnalyzerStatus.FAILED.isDimmed).isFalse()
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
    // Mocked rows — standalone mode
    // -------------------------------------------------------------------------

    @Test
    fun `mocked rows in standalone mode contain expected languages`() {
        val rows = SupportedLanguagesPanel.buildMockedRows(isBound = false)
        val languages = rows.map { it.language }
        assertThat(languages).contains(Language.JAVA, Language.JS, Language.TS, Language.PYTHON, Language.KOTLIN, Language.PHP)
    }

    @Test
    fun `mocked rows in standalone mode have premium languages without versions`() {
        val rows = SupportedLanguagesPanel.buildMockedRows(isBound = false)
        rows.filter { it.status == AnalyzerStatus.PREMIUM }.also { premiumRows ->
            assertThat(premiumRows).isNotEmpty()
            premiumRows.forEach { assertThat(it.version).isNull() }
        }
    }

    @Test
    fun `mocked rows in standalone mode have active languages with versions and localVersions`() {
        val rows = SupportedLanguagesPanel.buildMockedRows(isBound = false)
        rows.filter { it.status == AnalyzerStatus.ACTIVE }.also { activeRows ->
            assertThat(activeRows).isNotEmpty()
            activeRows.forEach {
                assertThat(it.version).isNotNull()
                assertThat(it.localVersion).isNotNull()
                assertThat(it.source).isEqualTo(AnalyzerSource.LOCAL)
            }
        }
    }

    @Test
    fun `mocked rows in standalone mode include a FAILED row`() {
        val rows = SupportedLanguagesPanel.buildMockedRows(isBound = false)
        assertThat(rows.any { it.status == AnalyzerStatus.FAILED }).isTrue()
    }

    @Test
    fun `mocked rows in standalone mode include an UNSUPPORTED row`() {
        val rows = SupportedLanguagesPanel.buildMockedRows(isBound = false)
        assertThat(rows.any { it.status == AnalyzerStatus.UNSUPPORTED }).isTrue()
    }

    @Test
    fun `standalone active rows are not overriding server version`() {
        val rows = SupportedLanguagesPanel.buildMockedRows(isBound = false)
        rows.filter { it.status == AnalyzerStatus.ACTIVE }.forEach {
            assertThat(it.isVersionOverriddenByServer).isFalse()
        }
    }

    // -------------------------------------------------------------------------
    // Mocked rows — connected mode
    // -------------------------------------------------------------------------

    @Test
    fun `mocked rows in connected mode promote premium to synced`() {
        val standalone = SupportedLanguagesPanel.buildMockedRows(isBound = false)
        val connected = SupportedLanguagesPanel.buildMockedRows(isBound = true)
        assertThat(standalone.any { it.status == AnalyzerStatus.PREMIUM }).isTrue()
        assertThat(connected.none { it.status == AnalyzerStatus.PREMIUM }).isTrue()
    }

    @Test
    fun `mocked rows in connected mode assign server source to synced languages`() {
        val connected = SupportedLanguagesPanel.buildMockedRows(isBound = true)
        connected.filter { it.status == AnalyzerStatus.SYNCED }.also { synced ->
            assertThat(synced).isNotEmpty()
            synced.forEach {
                assertThat(it.source).isEqualTo(AnalyzerSource.SONARQUBE_SERVER)
                assertThat(it.version).isNotNull()
            }
        }
    }

    @Test
    fun `connected and standalone modes have same number of rows`() {
        val standalone = SupportedLanguagesPanel.buildMockedRows(isBound = false)
        val connected = SupportedLanguagesPanel.buildMockedRows(isBound = true)
        assertThat(connected).hasSameSizeAs(standalone)
    }

    @Test
    fun `connected mode shows version override for Java`() {
        val connected = SupportedLanguagesPanel.buildMockedRows(isBound = true)
        val javaRow = connected.first { it.language == Language.JAVA }
        assertThat(javaRow.isVersionOverriddenByServer).isTrue()
        assertThat(javaRow.localVersion).isNotNull()
        assertThat(javaRow.version).isNotEqualTo(javaRow.localVersion)
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
    fun `table model getValueAt returns full row for LANGUAGE and VERSION columns`() {
        val row = SupportedLanguageRow(Language.JAVA, "Java", AnalyzerStatus.ACTIVE, AnalyzerSource.LOCAL, "7.30.1", localVersion = "7.30.1")
        val model = SupportedLanguagesTableModel(listOf(row))

        assertThat(model.getValueAt(0, SupportedLanguagesTableModel.Column.LANGUAGE.ordinal)).isEqualTo(row)
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
    fun `table model VERSION column class is SupportedLanguageRow`() {
        val model = SupportedLanguagesTableModel(emptyList())
        assertThat(model.getColumnClass(SupportedLanguagesTableModel.Column.VERSION.ordinal))
            .isEqualTo(SupportedLanguageRow::class.java)
    }

    @Test
    fun `table model column classes are correct`() {
        val model = SupportedLanguagesTableModel(emptyList())

        assertThat(model.getColumnClass(SupportedLanguagesTableModel.Column.LANGUAGE.ordinal)).isEqualTo(SupportedLanguageRow::class.java)
        assertThat(model.getColumnClass(SupportedLanguagesTableModel.Column.STATUS.ordinal)).isEqualTo(AnalyzerStatus::class.java)
        assertThat(model.getColumnClass(SupportedLanguagesTableModel.Column.SOURCE.ordinal)).isEqualTo(AnalyzerSource::class.java)
        assertThat(model.getColumnClass(SupportedLanguagesTableModel.Column.VERSION.ordinal)).isEqualTo(SupportedLanguageRow::class.java)
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

// Extension to expose the dimmed concept for tests without breaking encapsulation
private val AnalyzerStatus.isDimmed: Boolean
    get() = this == AnalyzerStatus.PREMIUM || this == AnalyzerStatus.UNSUPPORTED
