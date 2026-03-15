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

import javax.swing.table.AbstractTableModel

class SupportedLanguagesTableModel(private val rows: List<SupportedLanguageRow>) : AbstractTableModel() {

    enum class Column(val header: String) {
        ANALYSIS_TYPE("Analysis Type"),
        STATUS("Status"),
        SOURCE("Source"),
    }

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = Column.values().size

    override fun getColumnName(column: Int): String = Column.values()[column].header

    override fun getColumnClass(columnIndex: Int): Class<*> = when (Column.values()[columnIndex]) {
        Column.ANALYSIS_TYPE -> SupportedLanguageRow::class.java
        Column.STATUS -> AnalyzerStatus::class.java
        Column.SOURCE -> AnalyzerSource::class.java
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val row = rows[rowIndex]
        return when (Column.values()[columnIndex]) {
            Column.ANALYSIS_TYPE -> row
            Column.STATUS -> row.status
            Column.SOURCE -> row.source
        }
    }

    fun getRow(rowIndex: Int): SupportedLanguageRow = rows[rowIndex]
}
