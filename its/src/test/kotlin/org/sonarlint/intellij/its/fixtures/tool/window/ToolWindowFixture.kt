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
package org.sonarlint.intellij.its.fixtures.tool.window

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.DefaultXpath
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.fixtures.JButtonFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import java.time.Duration

fun ContainerFixture.toolWindow(
  title: String,
  function: ToolWindowFixture.() -> Unit = {}): ToolWindowFixture = step("Search for tool window with title $title") {
  val toolWindowPane = find<ToolWindowFixture>(Duration.ofSeconds(5))
  toolWindowPane.title = title
  toolWindowPane.apply(function)
}

@DefaultXpath(by = "ToolWindow type", xpath = "//div[@class='ToolWindowsPane']")
@FixtureName(name = "Tool Window")
class ToolWindowFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent) {
  var title: String? = null

  fun tab(title: String, function: TabTitleFixture.() -> Unit = {}) =
    find<TabTitleFixture>(byXpath("tab with title $title", "//div[@class='ContentTabLabel' and @text='$title']")).apply(function)

  fun tabTitleContains(text: String, function: TabTitleFixture.() -> Unit = {}): TabTitleFixture {
    return find<TabTitleFixture>(byXpath("tab with title $text", "//div[@class='ContentTabLabel' and contains(@text, '$text')]")).apply(function)
  }

  fun content(classType: String, function: TabContentFixture.() -> Unit = {}) =
    find<TabContentFixture>(byXpath("tab with content of type $classType", "//div[@class='$classType']")).apply(function)

  fun ensureOpen() {
    try {
      findToolWindowPanelTitle()
    } catch (e: Exception) {
      stripeButton().click()
    }
  }

  private fun findToolWindowPanelTitle() {
    find<ComponentFixture>(byXpath("//div[@accessiblename='$title:' and @class='BaseLabel' and @text='$title:']"))
  }

  private fun stripeButton() = find<JButtonFixture>(byXpath("//div[@accessiblename='$title' and @class='StripeButton' and @text='$title']"))

}
