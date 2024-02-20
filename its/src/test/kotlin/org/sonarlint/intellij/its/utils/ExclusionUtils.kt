/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import org.sonarlint.intellij.its.BaseUiTest.Companion.remoteRobot
import org.sonarlint.intellij.its.fixtures.clickWhenEnabled
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.jbTextFieldsWithBrowseButton
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
import java.time.Duration

class ExclusionUtils {

    companion object {

        fun excludeFile(fileName: String) {
            openExclusionsTab()
            excludeFileAndPressOk(fileName)
        }

        fun removeFileExclusion(fileName: String) {
            openExclusionsTab()
            removeSpecificExclusion(fileName)
        }

        private fun openExclusionsTab() {
            with(remoteRobot) {
                idea {
                    toolWindow("SonarLint") {
                        ensureOpen()
                        tabTitleContains("Current File") { select() }
                        content("CurrentFilePanel") {
                            toolBarButton("Configure SonarLint").click()
                        }
                        selectExclusionTab()
                    }
                }
            }
        }

        private fun selectExclusionTab() {
            with(remoteRobot) {
                idea {
                    dialog("Project Settings") {
                        findText("File Exclusions").click()
                    }
                }
            }
        }

        private fun removeSpecificExclusion(fileName: String) {
            with(remoteRobot) {
                idea {
                    dialog("Project Settings") {
                        findText("File Exclusions").click()
                        findText(fileName).click()
                        actionButton(ActionButtonFixture.byTooltipText("Remove")).click()
                        button("OK").click()
                    }
                }
            }
        }

        private fun excludeFileAndPressOk(fileName: String) {
            with(remoteRobot) {
                idea {
                    dialog("Project Settings") {
                        findText("File Exclusions").click()
                        actionButton(ActionButtonFixture.byTooltipText("Add")).clickWhenEnabled()
                        dialog("Add SonarLint File Exclusion") {
                            jbTextFieldsWithBrowseButton()[0].click()
                            keyboard { enterText(fileName) }
                            waitFor(Duration.ofSeconds(1)) {
                                button("OK").isEnabled()
                            }
                            button("OK").click()
                        }
                        button("OK").click()
                    }
                }
            }
        }

    }

}