/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2023 SonarSource
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
package org.sonarlint.intellij.actions

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import org.sonarlint.intellij.common.ui.SonarLintConsole
import org.sonarlint.intellij.config.Settings

class SonarFocusOnNewCode(private var isFocusOnNewCode: Boolean) : AbstractSonarToggleAction() {

    constructor() : this(false)

    fun isFocusOnNewCode(): Boolean = isFocusOnNewCode

    override fun isSelected(e: AnActionEvent): Boolean = isFocusOnNewCode

    override fun setSelected(e: AnActionEvent, isSelected: Boolean) {
        if (isFocusOnNewCode != isSelected) {
            isFocusOnNewCode = isSelected
            DataManager.getInstance().dataContextFromFocusAsync
                .onSuccess { Settings.getGlobalSettings().setFocusOnNewCode(ActionPlaces.TOOLWINDOW_CONTENT, it, isFocusOnNewCode) }
                .onError { e.project?.let { SonarLintConsole.get(it).error("Could not get data context for action to focus on new code") } }
        }
    }

}
