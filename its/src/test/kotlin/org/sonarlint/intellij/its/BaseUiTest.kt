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
import com.intellij.remoterobot.utils.keyboard
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.sonarlint.intellij.its.fixtures.IdeaFrame
import org.sonarlint.intellij.its.fixtures.clickWhenEnabled
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.openProjectFileBrowserDialog
import org.sonarlint.intellij.its.fixtures.searchField
import org.sonarlint.intellij.its.fixtures.settingsTree
import org.sonarlint.intellij.its.fixtures.welcomeFrame
import org.sonarlint.intellij.its.utils.VisualTreeDump
import org.sonarlint.intellij.its.utils.optionalStep
import java.awt.Point
import java.io.File
import java.time.Duration

const val robotUrl = "http://localhost:8082"

@ExtendWith(VisualTreeDump::class)
open class BaseUiTest {

  fun uiTest(url: String = robotUrl, test: RemoteRobot.() -> Unit) {
    RemoteRobot(url).apply(test)
  }

  fun openFile(remoteRobot: RemoteRobot, className: String) {
    with(remoteRobot) {
      idea {
        actionMenu("Navigate") {
          click()
          item("Class...") {
            // click at the left of the item to not move focus to another menu at the right
            click(Point(10, 10))
          }
        }
        searchField().text = className
        keyboard { enter() }
      }
    }
  }

  @BeforeEach
  fun cleanProject() = uiTest {
    clearConnections(this)
    goBackToWelcomeScreen(this)
  }

  private fun clearConnections(remoteRobot: RemoteRobot) {
    with(remoteRobot) {
      optionalIdeaFrame(this)?.apply {
        actionMenu("File") {
          click()
          // does not exist if no tool window is active
          item("Settings...") {
            click()
          }
        }
        dialog("Settings") {
          settingsTree {
            select("Tools/SonarLint")
          }
          // the view can take time to appear the first time
          val connectionsList = jList(JListFixture.byType(), Duration.ofSeconds(20))
          val removeButton = actionButton(byTooltipText("Remove"))
          while (connectionsList.items.isNotEmpty()) {
            removeButton.clickWhenEnabled()
            optionalStep {
              dialog("Connection In Use") {
                button("Yes").click()
              }
            }
          }
          button("OK").click()
        }
      }
    }
  }

  private fun optionalIdeaFrame(remoteRobot: RemoteRobot): IdeaFrame? {
    var ideaFrame: IdeaFrame? = null
    with(remoteRobot) {
      optionalStep {
        // we might be on the welcome screen
        ideaFrame = idea(Duration.ofSeconds(5))
      }
    }
    return ideaFrame
  }

  private fun goBackToWelcomeScreen(remoteRobot: RemoteRobot) {
    with(remoteRobot) {
      optionalIdeaFrame(this)?.apply {
        actionMenu("File") {
          click()
          item("Close Project") {
            click()
          }
        }
      }
    }
  }

  protected fun importTestProject(robot: RemoteRobot, projectName: String) {
    with(robot) {
      welcomeFrame {
        openProjectButton().click()
      }
      openProjectFileBrowserDialog {
        deleteIdeaProjectFiles(projectName)
        selectProjectFile(projectName)
      }
      idea {
        closeTipOfTheDay()
        waitBackgroundTasksFinished()
      }
    }
  }

  private fun deleteIdeaProjectFiles(projectName: String) {
    File("projects/$projectName", ".idea").deleteRecursively()
    File("projects/$projectName", "$projectName.iml").delete()
  }
}