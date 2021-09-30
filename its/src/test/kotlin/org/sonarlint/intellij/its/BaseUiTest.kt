/*
 * SonarLint for IntelliJ IDEA ITs
 * Copyright (C) 2015-2021 SonarSource
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
package org.sonarlint.intellij.its

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ActionButtonFixture.Companion.byTooltipText
import com.intellij.remoterobot.fixtures.JListFixture
import com.intellij.remoterobot.utils.waitFor
import org.assertj.swing.timing.Pause
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.sonarlint.intellij.its.fixtures.DialogFixture
import org.sonarlint.intellij.its.fixtures.IdeaFrame
import org.sonarlint.intellij.its.fixtures.clickWhenEnabled
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.editor
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.isCLion
import org.sonarlint.intellij.its.fixtures.openProjectFileBrowserDialog
import org.sonarlint.intellij.its.fixtures.preferencesDialog
import org.sonarlint.intellij.its.fixtures.tool.window.TabContentFixture
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
import org.sonarlint.intellij.its.fixtures.waitUntilLoaded
import org.sonarlint.intellij.its.fixtures.welcomeFrame
import org.sonarlint.intellij.its.utils.StepsLogger
import org.sonarlint.intellij.its.utils.VisualTreeDump
import org.sonarlint.intellij.its.utils.optionalStep
import java.awt.Point
import java.io.File
import java.time.Duration

const val robotUrl = "http://localhost:8082"

@ExtendWith(VisualTreeDump::class)
open class BaseUiTest {

    companion object {
        var remoteRobot: RemoteRobot

        init {
            StepsLogger.init()
            remoteRobot = RemoteRobot(robotUrl)
        }
    }

    fun uiTest(test: RemoteRobot.() -> Unit) {
        try {
            remoteRobot.apply(test)
        } finally {
            closeAllDialogs()
            optionalStep {
                sonarlintLogPanel(remoteRobot) {
                    println("SonarLint log outputs:")
                    findAllText { true }.forEach { println(it.text) }
                    toolBarButton("Clear SonarLint Console").click()
                }
            }
            if (remoteRobot.isCLion()) {
                optionalStep {
                    cmakePanel(remoteRobot) {
                        println("CMake log outputs:")
                        findAllText { true }.forEach { println(it.text) }
                        toolBarButton("Clear All").click()
                    }
                }
            }
        }
    }

    private fun sonarlintLogPanel(remoteRobot: RemoteRobot, function: TabContentFixture.() -> Unit = {}) {
        with(remoteRobot) {
            idea {
                toolWindow("SonarLint") {
                    ensureOpen()
                    tabTitleContains("Log") { select() }
                    content("SonarLintLogPanel") {
                        this.apply(function)
                    }
                }
            }
        }
    }

    private fun cmakePanel(remoteRobot: RemoteRobot, function: TabContentFixture.() -> Unit = {}) {
        with(remoteRobot) {
            idea {
                toolWindow("CMake") {
                    ensureOpen()
                    tabTitleContains("Debug") { select() }
                    content("DataProviderPanel") {
                        this.apply(function)
                    }
                }
            }
        }
    }

    fun openFile(filePath: String, fileName: String = filePath) {
        with(remoteRobot) {
            idea {
                runJs(
                    """
          const contentRoots = com.intellij.openapi.roots.ProjectRootManager.getInstance(component.project).getContentRoots();
          for (let i = 0; i < contentRoots.length; i++) {
            const file = contentRoots[i].findFileByRelativePath("$filePath");
            if (file) {
              new com.intellij.openapi.fileEditor.OpenFileDescriptor(component.project, file, 0).navigate(true);
              break;
            }
          }
        """, true
                )
                waitFor(Duration.ofSeconds(10)) { editor(fileName).isShowing }
                waitBackgroundTasksFinished()
            }
        }
    }

    @BeforeEach
    fun cleanProject() {
        closeAllDialogs()
        goBackToWelcomeScreen()
        clearConnections()
    }

    private fun closeAllDialogs() {
        remoteRobot.findAll<DialogFixture>(DialogFixture.all()).forEach {
            it.close()
        }
    }

    private fun clearConnections() {
        with(remoteRobot) {
            welcomeFrame {
                openPreferences()
                preferencesDialog {
                    // let the dialog settle (if we type the search query too soon it might be cleared for no reason)
                    Pause.pause(2000)

                    // Search for SonarLint because sometimes it is off the screen
                    search("SonarLint")

                    tree {
                        waitUntilLoaded()
                        // little trick to check if the search has been applied
                        waitFor(Duration.ofSeconds(10), Duration.ofSeconds(1)) { collectRows().size in 1..7 }
                        clickPath("Tools", "SonarLint")
                    }

                    val removeButton = actionButton(byTooltipText("Remove"))
                    jList(JListFixture.byType(), Duration.ofSeconds(20)) {
                        while (collectItems().isNotEmpty()) {
                            removeButton.clickWhenEnabled()
                            optionalStep {
                                dialog("Connection In Use") {
                                    button("Yes").click()
                                }
                            }
                        }
                    }
                    pressOk()
                }
            }
        }
    }

    private fun optionalIdeaFrame(remoteRobot: RemoteRobot): IdeaFrame? {
        var ideaFrame: IdeaFrame? = null
        with(remoteRobot) {
            optionalStep {
                // we might be on the welcome screen
                ideaFrame = idea(Duration.ofSeconds(1))
            }
        }
        return ideaFrame
    }

    private fun goBackToWelcomeScreen() {
        with(remoteRobot) {
            optionalIdeaFrame(this)?.apply {
                actionMenu("File") {
                    open()
                    item("Close Project") {
                        click()
                    }
                }
            }
        }
    }

    protected fun openExistingProject(projectName: String, isMaven: Boolean = false) {
        copyProjectFiles(projectName)
        with(remoteRobot) {
            welcomeFrame {
                // Force the click on the left: https://github.com/JetBrains/intellij-ui-test-robot/issues/19
                openProjectButton().click(Point(10, 10))
            }
            openProjectFileBrowserDialog {
                selectProjectFile(projectName, isMaven)
            }
            if (isCLion()) {
                optionalStep {
                    // from 2021.1+
                    dialog("Trust CMake Project?", Duration.ofSeconds(5)) {
                        button("Trust Project").click()
                    }
                }
            } else {
                optionalStep {
                    // from 2021.1+
                    dialog("Trust and Open Maven Project?", Duration.ofSeconds(5)) {
                        button("Trust Project").click()
                    }
                }
            }
            idea {
                closeTipOfTheDay()
                waitBackgroundTasksFinished()
            }
        }
    }

    private fun copyProjectFiles(projectName: String) {
        File("build/projects/$projectName").deleteRecursively()
        File("projects/$projectName").copyRecursively(File("build/projects/$projectName"))
    }
}
