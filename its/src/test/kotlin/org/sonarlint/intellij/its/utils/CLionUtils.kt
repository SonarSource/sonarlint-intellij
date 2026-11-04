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
import java.awt.Point
import java.time.Duration
import org.sonarlint.intellij.its.BaseUiTest.Companion.remoteRobot
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.findElement
import org.sonarlint.intellij.its.fixtures.idea

object CLionUtils {

    /**
     * Loads the CMake project of the CMakeLists.txt open in the editor, if CLion offers an editor link for it. Samples with
     * a `.idea` folder declare themselves CMake projects there (`CMakeWorkspace` in misc.xml), so recent versions load
     * them on open and show no link.
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
            if (!loadedFromLink) {
                println("ITs: no 'Load CMake project' link, the CMake project is loaded on open")
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
