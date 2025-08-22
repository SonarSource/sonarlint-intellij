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
package org.sonarlint.intellij.its.utils

import com.intellij.remoterobot.fixtures.ActionButtonFixture
import org.sonarlint.intellij.its.BaseUiTest
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
import org.sonarlint.intellij.its.utils.SettingsUtils.optionalIdeaFrame

object FiltersUtils {

    fun setFocusOnNewCode() {
        setFocusOnNewCode(true)
    }

    fun resetFocusOnNewCode() {
        setFocusOnNewCode(false)
    }

    fun setFocusOnNewCode(focusOnNewCode: Boolean) {
        optionalIdeaFrame()?.apply {
            toolWindow {
                tabTitleContains("Current File") { select() }
                content("CurrentFilePanel") {
                    val toolBarButton = focusOnNewCodeButton()
                    val isFocusActivated =
                        toolBarButton.popState() == ActionButtonFixture.PopState.SELECTED || toolBarButton.popState() == ActionButtonFixture.PopState.PUSHED
                    if (focusOnNewCode != isFocusActivated) {
                        toolBarButton.click()
                    }
                }
            }
        }
    }

    fun showResolvedIssues() {
        with(BaseUiTest.remoteRobot) {
            idea {
                toolWindow {
                    tabTitleContains("Current File") { select() }
                    content("CurrentFilePanel") {
                        resolvedIssuesButton().click()
                    }
                }
            }
        }
    }

}
