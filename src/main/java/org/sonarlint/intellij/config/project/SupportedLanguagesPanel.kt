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

import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import org.sonarlint.intellij.config.ConfigurationPanel

class SupportedLanguagesPanel : ConfigurationPanel<SonarLintProjectSettings> {

    private val panel: JPanel = JPanel(BorderLayout())

    init {
        panel.border = JBUI.Borders.empty(10)

        val emptyLabel = JLabel("Content will be available in a future version")
        emptyLabel.horizontalAlignment = JLabel.CENTER
        panel.add(emptyLabel, BorderLayout.CENTER)
    }

    override fun getComponent(): JComponent = panel

    override fun isModified(settings: SonarLintProjectSettings): Boolean = false

    override fun save(settings: SonarLintProjectSettings) {
        // No settings to save yet
    }

    override fun load(settings: SonarLintProjectSettings) {
        // No settings to load yet
    }

}
