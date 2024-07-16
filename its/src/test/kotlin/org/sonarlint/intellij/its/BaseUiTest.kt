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
package org.sonarlint.intellij.its

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.sonarlint.intellij.its.fixtures.DialogFixture
import org.sonarlint.intellij.its.fixtures.GotItTooltipFixture
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.isBuildCommunity
import org.sonarlint.intellij.its.fixtures.isBuildUltimate
import org.sonarlint.intellij.its.fixtures.isCLion
import org.sonarlint.intellij.its.fixtures.isGoLand
import org.sonarlint.intellij.its.fixtures.isGoPlugin
import org.sonarlint.intellij.its.fixtures.isIdea
import org.sonarlint.intellij.its.fixtures.isJavaScriptPlugin
import org.sonarlint.intellij.its.fixtures.isPhpStorm
import org.sonarlint.intellij.its.fixtures.isPyCharm
import org.sonarlint.intellij.its.fixtures.isRider
import org.sonarlint.intellij.its.fixtures.isSQLPlugin
import org.sonarlint.intellij.its.fixtures.tool.window.TabContentFixture
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
import org.sonarlint.intellij.its.tests.domain.CurrentFileTabTests.Companion.enableConnectedModeFromCurrentFilePanel
import org.sonarlint.intellij.its.utils.FiltersUtils.Companion.resetFocusOnNewCode
import org.sonarlint.intellij.its.utils.OpeningUtils.Companion.closeProject
import org.sonarlint.intellij.its.utils.StepsLogger
import org.sonarlint.intellij.its.utils.ThreadDumpOnFailure
import org.sonarlint.intellij.its.utils.VisualTreeDumpOnFailure
import org.sonarlint.intellij.its.utils.optionalStep

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
        fun isIdeaCommunity() = remoteRobot.isIdea() && remoteRobot.isBuildCommunity()

        @JvmStatic
        fun isIdeaUltimate() = remoteRobot.isIdea() && remoteRobot.isBuildUltimate()

        @JvmStatic
        fun isCLion() = remoteRobot.isCLion()

        @JvmStatic
        fun isPhpStorm() = remoteRobot.isPhpStorm()

        @JvmStatic
        fun isPyCharm() = remoteRobot.isPyCharm()

        @JvmStatic
        fun isRider() = remoteRobot.isRider()

        @JvmStatic
        fun isWebStorm() = remoteRobot.isIdea() && remoteRobot.isJavaScriptPlugin()

        /**
         *  This only checks for the GoLand IDE, if you want to check for the Go language support in general (via the
         *  Go plugin), use [BaseUiTest.isGoPlugin]!
         */
        @JvmStatic
        fun isGoLand() = remoteRobot.isGoLand()

        /**
         *  This one checks for the Go language support in general (via the Go plugin), if you want to check for the
         *  GoLand IDE, use [BaseUiTest.isGoLand]!
         */
        @JvmStatic
        fun isGoPlugin() = remoteRobot.isGoPlugin()

        @JvmStatic
        fun isSQLPlugin() = remoteRobot.isSQLPlugin() && isIdeaUltimate()

        private fun closeAllDialogs() {
            remoteRobot.findAll<DialogFixture>(DialogFixture.all()).forEach {
                it.close()
            }
        }
    }


    fun uiTest(test: RemoteRobot.() -> Unit) {
        try {
            remoteRobot.apply(test)
        } finally {
            closeAllDialogs()
            optionalStep {
                sonarlintLogPanel {
                    println("SonarLint log outputs:")
                    println(console().text)
                    toolBarButton("Clear SonarLint Console").click()
                }
            }
            if (remoteRobot.isCLion()) {
                optionalStep {
                    cmakePanel {
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

    private fun sonarlintLogPanel(function: TabContentFixture.() -> Unit = {}) {
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

    private fun cmakePanel(function: TabContentFixture.() -> Unit = {}) {
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

    private fun closeAllGotItTooltips() {
        var tries = 5
        var allGotItTooltips = remoteRobot.findAll(ComponentFixture::class.java, GotItTooltipFixture.firstButton())
        while (allGotItTooltips.isNotEmpty() && tries > 0) {
            allGotItTooltips.forEach {
                waitFor(Duration.ofSeconds(1)) {
                    it.isShowing
                }
                it.click()
            }
            allGotItTooltips = remoteRobot.findAll(ComponentFixture::class.java, GotItTooltipFixture.firstButton())
            tries--
        }
        if (5 - tries > 0) {
            println("Closed all Got It tooltips in ${5 - tries} tries")
        }
    }

    @BeforeEach
    fun quitProject() {
        closeAllGotItTooltips()
        closeAllDialogs()
    }

    @AfterEach
    fun disableConnectedMode() {
        resetFocusOnNewCode()
        enableConnectedModeFromCurrentFilePanel(null, false, "Orchestrator")
        closeProject()
    }

}
