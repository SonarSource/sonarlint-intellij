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
package org.sonarlint.intellij.ui.icons

import com.intellij.ui.scale.JBUIScale.isUsrHiDPI
import com.intellij.util.ui.EmptyIcon
import javax.swing.Icon
import org.sonarlint.intellij.ui.icons.CompoundIcon.Axis

class FindingIconBuilder private constructor(baseIcon: Icon?) {

    private var decoratingIcon = EMPTY_ICON
    private var undecorated = false
    private val primaryIcon: Icon = baseIcon ?: EMPTY_ICON
    private var isAiCodeFixable = false
    private var displayedStatus: DisplayedStatus = DisplayedStatus.OPEN

    fun build(): Icon {
        return CompoundIcon(Axis.X_AXIS, GAP, *buildIcons())
    }

    fun withDecoratingIcon(decoratingIcon: Icon?): FindingIconBuilder {
        this.decoratingIcon = decoratingIcon ?: EMPTY_ICON
        undecorated = false
        return this
    }

    fun withAiCodeFix(isAiCodeFixable: Boolean): FindingIconBuilder {
        this.isAiCodeFixable = isAiCodeFixable
        return this
    }

    fun withDisplayedStatus(displayedStatus: DisplayedStatus): FindingIconBuilder {
        this.displayedStatus = displayedStatus
        return this
    }

    fun undecorated(): FindingIconBuilder {
        this.undecorated = true
        return this
    }

    private fun buildIcons(): Array<Icon> {
        return if (undecorated) {
            arrayOf(getNormalOrStatusIcon())
        } else if (isAiCodeFixable) {
            arrayOf(decoratingIcon, getNormalOrStatusIcon(), SonarLintIcons.SPARKLE_GUTTER_ICON)
        } else {
            arrayOf(decoratingIcon, getNormalOrStatusIcon())
        }
    }

    private fun getNormalOrStatusIcon(): Icon {
        return when (displayedStatus) {
            DisplayedStatus.ACCEPTED -> SonarLintIcons.STATUS_ACCEPTED
            DisplayedStatus.FALSE_POSITIVE -> SonarLintIcons.STATUS_FALSE_POSITIVE
            DisplayedStatus.INVALID -> SonarLintIcons.STATUS_LINK_OFF
            else -> primaryIcon
        }
    }

    companion object {
        private val EMPTY_ICON = EmptyIcon.ICON_16
        private val GAP = if (isUsrHiDPI) 8 else 4

        @JvmStatic
        fun forBaseIcon(baseIcon: Icon?): FindingIconBuilder {
            return FindingIconBuilder(baseIcon ?: EMPTY_ICON)
        }
    }
}
