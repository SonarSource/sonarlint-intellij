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
package org.sonarlint.intellij.its.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.DefaultXpath
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.WaitForConditionTimeoutException
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration

fun RemoteRobot.idea(duration: Duration = Duration.ofSeconds(20), function: IdeaFrame.() -> Unit = {}): IdeaFrame {
  return find<IdeaFrame>(duration).apply(function)
}

@FixtureName("Idea frame")
@DefaultXpath("IdeFrameImpl type", "//div[@class='IdeFrameImpl']")
class IdeaFrame(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent) {

  private val ideStatusBar
    get() = find(IdeStatusBarFixture::class.java)

  private fun isBackgroundTaskRunning(): Boolean {
    for (i in 0..1) {
      try {
        ideStatusBar.backgroundTaskPendingIcon
        println("Task running")
        return true
      } catch (timeoutException: WaitForConditionTimeoutException) {
        try {
          ideStatusBar.pauseButton
          println("Task running")
          return true
        } catch (timeoutException: WaitForConditionTimeoutException) {
          try {
            // could be between 2 background tasks, wait and retry
            Thread.sleep(1000)
          } catch (e: InterruptedException) {
            e.printStackTrace()
          }
        }
      }
    }
    println("No tasks running")
    return false
  }

  fun waitBackgroundTasksFinished() {
    println("Check background tasks")
    waitFor(Duration.ofMinutes(5), Duration.ofSeconds(1), "Some background tasks are still running") {
      !isBackgroundTaskRunning()
    }
    println("Check background tasks - done")
  }

  fun actionMenu(label: String, function: ActionMenuFixture.() -> Unit): ActionMenuFixture {
    return findAll<ActionMenuFixture>(byXpath("menu $label", "//div[@class='ActionMenu' and @text='$label']"))[0].apply(function)
  }

  fun actionHyperLink(accessiblename: String, function: ActionHyperLinkFixture.() -> Unit): ActionHyperLinkFixture {
    return findElement<ActionHyperLinkFixture>(byXpath("link $accessiblename", "//div[@accessiblename='$accessiblename' and @class='ActionHyperlinkLabel']")).apply(function)
  }

  fun openSettings() {
    actionMenu("File") {
      open()
      item("Settings...") {
        click()
      }
    }
  }

}
