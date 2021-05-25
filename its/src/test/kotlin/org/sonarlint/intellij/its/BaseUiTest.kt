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
import com.intellij.remoterobot.fixtures.ActionButtonFixture
import com.intellij.remoterobot.fixtures.ActionButtonFixture.Companion.byTooltipText
import com.intellij.remoterobot.fixtures.ActionButtonFixture.Companion.byTooltipTextContains
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.JListFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.StepLogger
import com.intellij.remoterobot.stepsProcessing.StepWorker
import com.intellij.remoterobot.utils.waitFor
import org.assertj.swing.timing.Pause
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.sonarlint.intellij.its.fixtures.*
import org.sonarlint.intellij.its.fixtures.tool.window.TabContentFixture
import org.sonarlint.intellij.its.fixtures.tool.window.toolWindow
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
          System.out.println("SonarLint log outputs:");
          findAllText{ true }.forEach{ System.out.println(it.text) }
          toolBarButton("Clear SonarLint Console").click()
        }
      }
      if (remoteRobot.isCLion()) {
        optionalStep {
          cmakePanel(remoteRobot) {
            System.out.println("CMake log outputs:");
            findAllText{ true }.forEach{ System.out.println(it.text) }
            toolBarButton("Clear All").click()
          }
        }
      }
    }
  }

  fun sonarlintLogPanel(remoteRobot: RemoteRobot, function: TabContentFixture.() -> Unit = {}): Unit {
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

  fun cmakePanel(remoteRobot: RemoteRobot, function: TabContentFixture.() -> Unit = {}): Unit {
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

  fun openClass(remoteRobot: RemoteRobot, className: String) {
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
        val fileList = jList(JListFixture.byType(), Duration.ofSeconds(5))
        waitFor(Duration.ofSeconds(5)) { fileList.collectItems().isNotEmpty() }
        fileList.clickItemAtIndex(0)
        waitFor(Duration.ofSeconds(10)) { editor("$className.java").isShowing }
        waitBackgroundTasksFinished()
      }
    }
  }

  fun openFile(fileName: String) {
    with(remoteRobot) {
      idea {
        actionMenu("Navigate") {
          click()
          item("File...") {
            // click at the left of the item to not move focus to another menu at the right
            click(Point(10, 10))
          }
        }
        searchField().text = fileName
        val fileList = jList(JListFixture.byType(), Duration.ofSeconds(5))
        waitFor(Duration.ofSeconds(5)) { fileList.collectItems().isNotEmpty() }
        fileList.clickItemAtIndex(0)
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
    remoteRobot.findAll<DialogFixture>(DialogFixture.all()).forEach{
      it.close()
    }
  }

  private fun clearConnections() {
    with(remoteRobot) {
      welcomeFrame() {
        openPreferences()
        preferencesDialog {
          // Search for SonarLint because sometimes it is off the screen
          search("SonarLint")

          // The search sometimes take time to filter the tree
          Pause.pause(1000)

          selectPreferencePage("Tools", "SonarLint")

          // Doesn't work
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
          click()
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
