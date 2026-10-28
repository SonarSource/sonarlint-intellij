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
import java.io.File
import java.time.Duration
import org.sonarlint.intellij.its.BaseUiTest.Companion.isRider
import org.sonarlint.intellij.its.BaseUiTest.Companion.remoteRobot
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.findElement
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.isCLion
import org.sonarlint.intellij.its.fixtures.isRider
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
            // Open through the platform API rather than the Open dialog: the dialog differs per IDE and
            // version (2026.2 replaced its path field with a breadcrumb bar), and no test is about it.
            // The IDE's open processors still run, so the Trust/Rider/CLion prompts below still appear.
            val projectDir = File("projects/$projectName-tmp").absoluteFile.normalize()
            // CLion only imports the CMake project (instead of a plain folder) when opened from CMakeLists.txt
            val cmakeLists = projectDir.resolve("CMakeLists.txt")
            val toOpen = if (isCLion() && cmakeLists.exists()) cmakeLists else projectDir
            runJs(
                """
                    const toOpen = new java.io.File("${toOpen.path.replace("\\", "\\\\")}").toPath();
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() =>
                        com.intellij.ide.impl.ProjectUtil.openOrImport(toOpen, null, true));
                """
            )
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
