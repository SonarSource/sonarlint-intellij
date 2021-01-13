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
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.WaitForConditionTimeoutException
import com.intellij.remoterobot.utils.waitFor
import org.sonarlint.intellij.its.utils.optionalStep
import java.time.Duration

fun RemoteRobot.idea(duration: Duration = Duration.ofSeconds(20), function: IdeaFrame.() -> Unit = {}): IdeaFrame {
  return find<IdeaFrame>(duration).apply(function)
}

@FixtureName("Idea frame")
@DefaultXpath("IdeFrameImpl type", "//div[@class='IdeFrameImpl']")
class IdeaFrame(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent) {

  private val ideStatusBar
    get() = find(IdeStatusBarFixture::class.java)

  @JvmOverloads
  fun dumbAware(timeout: Duration = Duration.ofMinutes(5), function: () -> Unit) {
    step("Wait for smart mode") {
      waitFor(duration = timeout, interval = Duration.ofSeconds(5)) {
        runCatching { isDumbMode().not() }.getOrDefault(false)
      }
      function()
      step("..wait for smart mode again") {
        waitFor(duration = timeout, interval = Duration.ofSeconds(5)) {
          isDumbMode().not()
        }
      }
    }
  }

  private fun isDumbMode(): Boolean {
    return callJs("!component.project || com.intellij.openapi. project.DumbService.isDumb(component.project);", true)
  }

  fun closeTipOfTheDay() {
    dumbAware {
      optionalStep {
        dialog("Tip of the Day") {
          button("Close").click()
        }
      }
    }
  }

  private fun isBackgroundTaskRunning(): Boolean {
    for (i in 0..4) {
      try {
        ideStatusBar.backgroundTaskPendingIcon
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
    return false
  }

  fun waitBackgroundTasksFinished() {
    waitFor(Duration.ofMinutes(2), Duration.ofSeconds(5), "Some background tasks are still running") {
      !isBackgroundTaskRunning()
    }
  }

  fun actionMenu(label: String, function: ActionMenuFixture.() -> Unit): ActionMenuFixture {
    return findAll<ActionMenuFixture>(byXpath("menu $label", "//div[@class='ActionMenu' and @text='$label']"))[0].apply(function)
  }

}
