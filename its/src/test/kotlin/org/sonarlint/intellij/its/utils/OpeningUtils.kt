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

    private const val OPEN_ERROR_PROPERTY = "sonarlint.its.open.error"

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
            val projectDir = File("projects/$projectName-tmp").absoluteFile.normalize().path.replace("\\", "\\\\")
            // Errors thrown inside invokeLater never reach Remote Robot, so keep them for the failure message
            runJs(
                """
                    const projectDir = new java.io.File("$projectDir").toPath();
                    java.lang.System.clearProperty("$OPEN_ERROR_PROPERTY");
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() => {
                        try {
                            com.intellij.ide.impl.ProjectUtil.openOrImport(projectDir, null, true);
                        } catch (e) {
                            java.lang.System.setProperty("$OPEN_ERROR_PROPERTY", String(e));
                        }
                    });
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
            try {
                idea {
                    waitBackgroundTasksFinished()
                }
            } catch (e: Throwable) {
                val openError = callJs<String>("""java.lang.System.getProperty("$OPEN_ERROR_PROPERTY", "")""")
                if (openError.isNotEmpty()) {
                    e.addSuppressed(IllegalStateException("Opening $projectName failed in the IDE: $openError"))
                }
                throw e
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
