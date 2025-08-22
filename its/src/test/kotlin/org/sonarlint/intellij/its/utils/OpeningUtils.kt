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

import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.JListFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.keyboard
import org.sonarlint.intellij.its.BaseUiTest.Companion.isRider
import org.sonarlint.intellij.its.BaseUiTest.Companion.remoteRobot
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.findElement
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.isCLion
import org.sonarlint.intellij.its.fixtures.isGoLand
import org.sonarlint.intellij.its.fixtures.isRider
import org.sonarlint.intellij.its.fixtures.openProjectFileBrowserDialog
import org.sonarlint.intellij.its.fixtures.openSolutionBrowserDialog
import org.sonarlint.intellij.its.fixtures.welcomeFrame
import org.sonarlint.intellij.its.utils.SettingsUtils.optionalIdeaFrame
import java.awt.Point
import java.io.File
import java.time.Duration

object OpeningUtils {

    fun openFile(filePath: String, fileName: String = filePath) {
        with(remoteRobot) {
            idea {
                runJs(
                    """
                        const file = component.project.getBaseDir().findFileByRelativePath("$filePath");
                        if (file) {
                            const openDescriptor = new com.intellij.openapi.fileEditor.OpenFileDescriptor(component.project, file);
                            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() => openDescriptor.navigateInEditor(component.project, true));
                        }
                        else {
                            throw "Cannot open file '" + $filePath +"': not found";
                        }
        """, true
                )
                waitBackgroundTasksFinished()
            }
        }
    }

    fun openFileSelector() {
        with(remoteRobot) {
            idea {
                optionalStep {
                    findElement<ComponentFixture>(byXpath("//div[@tooltiptext='Main Menu']")).click()
                }
                actionMenu("File") {
                    item("Open") {
                        click(Point(10, 10)) // Force the click on the left
                    }
                }
            }
        }
    }

    fun openFileViaMenu(fileName: String) {
        with(remoteRobot) {
            idea {
                optionalStep {
                    findElement<ComponentFixture>(byXpath("//div[@tooltiptext='Main Menu']")).click()
                }
                actionMenu("Navigate") {
                    moveMouse()
                    optionalStep {
                        open()
                    }
                    item("File") {
                        click()
                    }
                    keyboard {
                        enterText(fileName)
                        enter()
                    }
                }
                waitBackgroundTasksFinished()
            }
        }
    }

    fun openExistingProject(projectName: String, copyProjectFiles: Boolean = true) {
        if (copyProjectFiles) {
            copyProjectFiles(projectName)
        }
        with(remoteRobot) {
            try {
                welcomeFrame {
                    // Force the click on the left: https://github.com/JetBrains/intellij-ui-test-robot/issues/19
                    openProjectButton().click(Point(10, 10))
                }
            } catch (e: Throwable) {
                // Starting from 2025.2+ there's no welcome frame
                if (isGoLand()) {
                    openFileSelector()
                } else {
                    throw e
                }
            }
            if (remoteRobot.isRider()) {
                openSolutionBrowserDialog {
                    selectProjectFile(projectName)
                }
            } else {
                openProjectFileBrowserDialog {
                    selectProjectFile(projectName)
                }
            }
            if (!remoteRobot.isCLion()) {
                optionalStep {
                    dialog("Trust and Open Maven Project?", Duration.ofSeconds(5)) {
                        button("Trust Project").click()
                    }
                }
            }
            if (remoteRobot.isRider()) {
                optionalStep {
                    dialog("Select a Solution to Open") {
                        jList(JListFixture.byType()) {
                            clickItemAtIndex(0)
                            button("Open").click()
                        }
                    }
                }
            }
            idea {
                waitBackgroundTasksFinished()
            }
            if (remoteRobot.isCLion()) {
                optionalStep {
                    dialog("Open Project Wizard") {
                        button("OK").click()
                    }
                }
            }
        }
    }

    fun closeProject() {
        optionalIdeaFrame()?.apply {
            optionalStep {
                findElement<ComponentFixture>(byXpath("//div[@tooltiptext='Main Menu']")).click()
            }
            actionMenu("File") {
                optionalStep {
                    open()
                }
                val name = if (isRider()) "Close Solution" else "Close Project"
                item(name) {
                    click()
                }
            }
        }
    }

    private fun copyProjectFiles(projectName: String) {
        File("projects/$projectName-tmp").deleteRecursively()
        File("projects/$projectName").copyRecursively(File("projects/$projectName-tmp"))
    }

}
