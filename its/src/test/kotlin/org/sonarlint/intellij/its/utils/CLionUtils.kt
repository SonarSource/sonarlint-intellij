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
import com.intellij.remoterobot.utils.waitFor
import java.awt.Point
import java.awt.event.KeyEvent
import java.io.File
import java.net.URL
import java.time.Duration
import org.sonarlint.intellij.its.BaseUiTest.Companion.remoteRobot
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.editorComponent
import org.sonarlint.intellij.its.fixtures.findElement
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.robotUrl

object CLionUtils {

    private const val LOAD_CMAKE_PROJECT_ACTION = "Load CMake Project"

    /**
     * Loads the CMake project of the CMakeLists.txt open in the editor. Older CLion versions offer an editor link for it;
     * from 2026.2 the sample (it has `.idea`) opens as a plain project without that link, and the editor context menu has
     * no such item, so run the action through Find Action.
     */
    fun loadCMakeProject() {
        with(remoteRobot) {
            val loadedFromLink = runCatching {
                idea {
                    actionHyperLink("Load CMake project") {
                        click()
                    }
                }
            }.isSuccess
            if (!loadedFromLink && !runFromFindAction(LOAD_CMAKE_PROJECT_ACTION)) {
                // Neither is offered once the CMake project is loaded, e.g. by the Open Project Wizard on older versions
                println("ITs: no 'Load CMake project' link or action, assuming the CMake project is already loaded")
            }
        }
        confirmCMakeProjectDialogs(trustTimeout = Duration.ofSeconds(5), wizardTimeout = Duration.ofSeconds(5))
        optionalStep {
            // The toolchain may need fixing on CI machines
            remoteRobot.idea {
                actionHyperLink("Fix…") {
                    click()
                }
                findElement<ComponentFixture>(byXpath("//div[@class='MyList']")).click(Point(10, 10))
            }
        }
    }

    /** Runs an enabled action by name through Find Action, from the focused editor; false if it is not offered. */
    private fun runFromFindAction(actionName: String): Boolean {
        with(remoteRobot) {
            idea {
                editorComponent().click()
            }
            keyboard {
                hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_A)
                enterText(actionName)
            }
            val offered = runCatching {
                waitFor(Duration.ofSeconds(10), Duration.ofMillis(500), "'$actionName' in Find Action") {
                    findAll<ComponentFixture>(
                        // The results list, not the search field, which also shows the typed text
                        byXpath("//div[@class='SearchEverywhereUI']//div[@class='JBList' and contains(@visible_text, '$actionName')]")
                    ).isNotEmpty()
                }
            }.isSuccess
            if (!offered) {
                saveVisualTree("find-action-${actionName.replace(' ', '-')}")
            }
            keyboard {
                if (offered) enter() else escape()
            }
            return offered
        }
    }

    /** Keeps the component tree with the CI reports, to see what the popup offered instead. */
    private fun saveVisualTree(name: String) {
        runCatching {
            val file = File("build/reports/its-diagnostics/$name.html").apply { parentFile.mkdirs() }
            val connection = URL(robotUrl).openConnection().apply {
                connectTimeout = 1_000
                readTimeout = 10_000
            }
            file.writeText(connection.getInputStream().reader().use { it.readText() })
            println("ITs: component tree written to ${file.absolutePath}")
        }.onFailure { println("ITs: could not save the component tree: $it") }
    }

    /** Confirms the dialogs CLion may show when it opens or loads a CMake project; usually neither shows. */
    fun confirmCMakeProjectDialogs(trustTimeout: Duration = Duration.ofSeconds(5), wizardTimeout: Duration = Duration.ofSeconds(20)) {
        with(remoteRobot) {
            optionalStep {
                dialog("Trust CMake Project?", trustTimeout) {
                    button("Trust Project").click()
                }
            }
            optionalStep {
                dialog("Open Project Wizard", wizardTimeout) {
                    button("OK").click()
                }
            }
        }
    }
}
