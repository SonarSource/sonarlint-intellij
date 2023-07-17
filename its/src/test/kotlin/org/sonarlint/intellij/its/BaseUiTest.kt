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
package org.sonarlint.intellij.its

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ActionButtonFixture.Companion.byTooltipText
import com.intellij.remoterobot.fixtures.JListFixture
import com.intellij.remoterobot.utils.waitFor
import org.assertj.core.api.Assertions
import org.assertj.swing.timing.Pause
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.sonarlint.intellij.its.fixtures.DialogFixture
import org.sonarlint.intellij.its.fixtures.IdeaFrame
import org.sonarlint.intellij.its.fixtures.PreferencesDialog
import org.sonarlint.intellij.its.fixtures.clickWhenEnabled
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.editor
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.isCLion
import org.sonarlint.intellij.its.fixtures.isGoLand
import org.sonarlint.intellij.its.fixtures.isGoPlugin
import org.sonarlint.intellij.its.fixtures.isSQLPlugin
import org.sonarlint.intellij.its.fixtures.openProjectFileBrowserDialog
import org.sonarlint.intellij.its.fixtures.preferencesDialog
import org.sonarlint.intellij.its.fixtures.tool.window.TabContentFixture
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
import org.sonarlint.intellij.its.fixtures.waitUntilLoaded
import org.sonarlint.intellij.its.fixtures.welcomeFrame
import org.sonarlint.intellij.its.utils.StepsLogger
import org.sonarlint.intellij.its.utils.ThreadDumpOnFailure
import org.sonarlint.intellij.its.utils.VisualTreeDumpOnFailure
import org.sonarlint.intellij.its.utils.optionalStep
import java.awt.Point
import java.io.File
import java.time.Duration

const val robotUrl = "http://localhost:8082"

@ExtendWith(VisualTreeDumpOnFailure::class)
@ExtendWith(ThreadDumpOnFailure::class)
open class BaseUiTest {

    companion object {
        var remoteRobot: RemoteRobot

        init {
            StepsLogger.init()
            remoteRobot = RemoteRobot(robotUrl)
        }

        @JvmStatic
        fun isCLionOrGoLand(): Boolean {
            return remoteRobot.isCLion() || remoteRobot.isGoLand()
        }

        @JvmStatic
        fun isCLion(): Boolean {
            return remoteRobot.isCLion()
        }

        /**
         *  This only checks for the GoLand IDE, if you want to check for the Go language support in general (via the
         *  Go plugin), use [BaseUiTest.isGoPlugin]!
         */
        @JvmStatic
        fun isGoLand(): Boolean {
            return remoteRobot.isGoLand()
        }

        /**
         *  This one checks for the Go language support in general (via the Go plugin), if you want to check for the
         *  GoLand IDE, use [BaseUiTest.isGoLand]!
         */
        @JvmStatic
        fun isGoPlugin(): Boolean = remoteRobot.isGoPlugin()

        @JvmStatic
        fun isSQLPlugin(): Boolean = remoteRobot.isSQLPlugin()
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
            failTestIfUncaughtExceptions()
        }
    }

    private fun failTestIfUncaughtExceptions() {
        val uncaughtExceptions = getUncaughtExceptions()
        val shouldFailTest = uncaughtExceptions.any { e -> e.contains("sonarlint", true) || e.contains("sonarsource", true) }
        uncaughtExceptions.forEach { e -> println("Uncaught error during the test: $e") }
        clearExceptions()
        if (shouldFailTest) {
            fail("There were uncaught exceptions during the test, see logs")
        }
    }

    private fun getUncaughtExceptions(): List<String> {
        return remoteRobot.callJs(
            """
        const result = new ArrayList()
        com.intellij.diagnostic.MessagePool.getInstance().getFatalErrors(true, true)
            .forEach((error) => result.add("message=" + error.getMessage() + ", stacktrace=" + error.getThrowableText()))
        result
    """
        )
    }

    private fun clearExceptions() {
        remoteRobot.runJs("com.intellij.diagnostic.MessagePool.getInstance().clearErrors()")
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
                        const file = component.project.getBaseDir().findFileByRelativePath("$filePath");
                        if (file) {
                            const openDescriptor = new com.intellij.openapi.fileEditor.OpenFileDescriptor(component.project, file);
                            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() => openDescriptor.navigate(true));
                        }
                        else {
                            throw "Cannot open file '" + $filePath +"': not found";
                        }
        """, false
                )
                waitFor(Duration.ofSeconds(10)) { editor(fileName).isShowing }
                waitBackgroundTasksFinished()
            }
        }
    }

    protected fun verifyCurrentFileTabContainsMessages(vararg expectedMessages: String) {
        with(remoteRobot) {
            idea {
                toolWindow("SonarLint") {
                    ensureOpen()
                    tabTitleContains("Current File") { select() }
                    content("CurrentFilePanel") {
                        expectedMessages.forEach {
                            Assertions.assertThat(hasText(it)).`as`("Failed to find current file text '$it'").isTrue()
                        }
                    }
                }
            }
        }
    }

    protected fun clickCurrentFileIssue(issueMessage: String) {
        with(remoteRobot) {
            idea {
                toolWindow("SonarLint") {
                    ensureOpen()
                    tabTitleContains("Current File") { select() }
                    content("CurrentFilePanel") {
                        findText(issueMessage).click()
                    }
                }
            }
        }
    }

    protected fun verifyRuleDescriptionTabContains(expectedMessage: String) {
        with(remoteRobot) {
            idea {
                toolWindow("SonarLint") {
                    ensureOpen()
                    content("CurrentFilePanel") {
                        waitFor(Duration.ofSeconds(10), errorMessage = "Unable to find '$expectedMessage' in: ${findAllText()}") {
                            hasText(
                                expectedMessage
                            )
                        }
                    }
                }
            }
        }
    }

    protected fun verifyCurrentFileShowsCard(expectedClass: String) {
        with(remoteRobot) {
            idea {
                toolWindow("SonarLint") {
                    ensureOpen()
                    Assertions.assertThat(findCard(expectedClass)).isNotNull
                }
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

    private fun settings(function: PreferencesDialog.() -> Unit) {
        with(remoteRobot) {
            try {
                welcomeFrame {
                    openPreferences()
                }
            } catch (e: Exception) {
                idea {
                    openSettings()
                }
            }
            preferencesDialog {
                function(this)
            }
        }
    }

    protected fun sonarLintGlobalSettings(function: PreferencesDialog.() -> Unit) {
        settings {
            // let the dialog settle (if we type the search query too soon it might be cleared for no reason)
            Pause.pause(2000)

            // Search for SonarLint because sometimes it is off the screen
            search("SonarLint")

            tree {
                waitUntilLoaded()
                // little trick to check if the search has been applied
                waitFor(Duration.ofSeconds(10), Duration.ofSeconds(1)) { collectRows().size in 1..10 }
                clickPath("Tools", "SonarLint")
            }

            // let the SonarLint view settle (sometimes the UI thread blocks for a few seconds)
            Pause.pause(4000)

            function(this)
        }
    }

    private fun clearConnections() {
        sonarLintGlobalSettings {
            val removeButton = actionButton(byTooltipText("Remove"))
            jList(JListFixture.byType()) {
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
            if (!remoteRobot.isCLion()) {
                optionalStep {
                    // from 2020.3.4+
                    dialog("Trust and Open Maven Project?", Duration.ofSeconds(5)) {
                        button("Trust Project").click()
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
            idea {
                // corresponding system property has been introduced around middle of 2020
                // removable at some point when raising minimal version
                closeTipOfTheDay()
            }
        }
    }

    private fun copyProjectFiles(projectName: String) {
        File("build/projects/$projectName").deleteRecursively()
        File("projects/$projectName").copyRecursively(File("build/projects/$projectName"))
    }
}
