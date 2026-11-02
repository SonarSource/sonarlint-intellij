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
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.keyboard
import java.awt.Point
import java.io.File
import java.net.URL
import org.sonarlint.intellij.its.BaseUiTest.Companion.remoteRobot
import org.sonarlint.intellij.its.fixtures.findElement
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.welcomeFrame
import org.sonarlint.intellij.its.robotUrl

/**
 * TEMPORARY (SLI-3028 review, remove before merge): captures the Open dialog of the IDE under test, to find out
 * whether ITs can open projects through it again instead of the platform API. Runs once per test JVM, never fails
 * the test, and writes the component trees to build/reports/open-dialog-investigation (uploaded with the CI reports).
 */
object OpenDialogInvestigation {

    private var captured = false
    private val outputDirectory = File("build/reports/open-dialog-investigation")

    fun captureOnce(projectPath: String) {
        if (captured) return
        captured = true
        runCatching {
            openDialog()
            Thread.sleep(2_000)
            dump("1-dialog-open")
            // Does the new dialog accept a typed path, e.g. by jumping to it or showing a path field?
            remoteRobot.keyboard { enterText(projectPath) }
            Thread.sleep(2_000)
            dump("2-path-typed")
        }.onFailure { println("ITs: open dialog capture failed: $it") }
        closeDialogs()
    }

    private fun openDialog() {
        with(remoteRobot) {
            val fromWelcomeScreen = runCatching {
                welcomeFrame {
                    openProjectButton().click(Point(10, 10))
                }
            }.isSuccess
            if (!fromWelcomeScreen) {
                idea {
                    runCatching { findElement<ComponentFixture>(byXpath("//div[@tooltiptext='Main Menu']")).click() }
                    actionMenu("File") {
                        item("Open") {
                            click(Point(10, 10))
                        }
                    }
                }
            }
            println("ITs: open dialog requested from ${if (fromWelcomeScreen) "the welcome screen" else "File > Open"}")
        }
    }

    private fun dump(step: String) {
        outputDirectory.mkdirs()
        val connection = URL(robotUrl).openConnection().apply {
            connectTimeout = 1_000
            readTimeout = 10_000
        }
        val file = File(outputDirectory, "$step.html")
        file.writeText(connection.getInputStream().reader().use { it.readText() })
        println("ITs: open dialog component tree written to ${file.absolutePath}")
    }

    /** The project is then opened through the platform API, which a modal dialog left open would block. */
    private fun closeDialogs() {
        repeat(3) {
            val dialogs = runCatching { remoteRobot.findAll<ComponentFixture>(byXpath("//div[@class='MyDialog']")) }.getOrDefault(emptyList())
            if (dialogs.isEmpty()) return
            runCatching { remoteRobot.keyboard { escape() } }
            Thread.sleep(1_000)
        }
        println("ITs: open dialog capture could not close every dialog")
    }
}
