/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
import java.awt.Point
import java.io.File
import java.time.Duration
import org.sonarlint.intellij.its.BaseUiTest.Companion.isRider
import org.sonarlint.intellij.its.BaseUiTest.Companion.remoteRobot
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.findElement
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.isCLion
import org.sonarlint.intellij.its.fixtures.isRider
import org.sonarlint.intellij.its.fixtures.openProjectFileBrowserDialog
import org.sonarlint.intellij.its.fixtures.openSolutionBrowserDialog
import org.sonarlint.intellij.its.fixtures.welcomeFrame
import org.sonarlint.intellij.its.utils.SettingsUtils.optionalIdeaFrame

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

    /**
     * Runs File | Open without going through the menu, whose layout differs between IDEs and on the welcome screen that some
     * IDEs show as a project window (2025.3+ PyCharm, 2026.3+ EAPs). Takes the first "Open" entry of the IDE's own File menu
     * (Rider has its own), else the platform action. Runs later on the EDT, as the dialog it opens is modal.
     */
    fun openFileSelector() {
        with(remoteRobot) {
            idea {
                runJs(
                    """
                        const actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance();
                        function findOpenAction(group) {
                            const children = group.getChildActionsOrStubs();
                            for (let i = 0; i < children.length; i++) {
                                const child = children[i];
                                if (child instanceof com.intellij.openapi.actionSystem.DefaultActionGroup) {
                                    const found = findOpenAction(child);
                                    if (found) return found;
                                } else {
                                    const text = child.getTemplateText();
                                    // Children can be unloaded stubs, so resolve the action by id
                                    if (text && new String(text).startsWith("Open")) return actionManager.getAction(actionManager.getId(child));
                                }
                            }
                            return null;
                        }
                        const fileMenu = actionManager.getAction("FileMenu");
                        const action = (fileMenu && findOpenAction(fileMenu)) || actionManager.getAction("OpenFile");
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() =>
                            actionManager.tryToExecute(action, null, component, "MainMenu", true));
                    """
                )
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
                // Starting from 2025.2+ there's no welcome frame in any IDE flavor
                try {
                    openFileSelector()
                } catch (fallbackFailure: Throwable) {
                    e.addSuppressed(fallbackFailure)
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
                CLionUtils.confirmCMakeProjectDialogs()
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
