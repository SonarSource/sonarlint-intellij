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
package org.sonarlint.intellij.its.fixtures.tool.window

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.DefaultXpath
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import org.sonarlint.intellij.its.fixtures.findElement
import org.sonarlint.intellij.its.fixtures.stripeButton
import java.time.Duration

fun ContainerFixture.toolWindow(
  title: String,
  function: ToolWindowFixture.() -> Unit = {}): ToolWindowFixture = step("Search for tool window with title $title") {
  val toolWindowPane = find<ToolWindowFixture>(Duration.ofSeconds(5))
  toolWindowPane.title = title
  toolWindowPane.apply(function)
}

// Panel class name changed from ToolWindowsPane to ToolWindowPane in 2022.x
@DefaultXpath(by = "ToolWindow type", xpath = "//div[@class='ToolWindowsPane' or @class='ToolWindowPane']")
@FixtureName(name = "Tool Window")
class ToolWindowFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent) {
  lateinit var title: String

  fun tab(title: String, function: TabTitleFixture.() -> Unit = {}) =
    findElement<TabTitleFixture>(byXpath("tab with title $title", "//div[@class='ContentTabLabel' and @text='$title']")).apply(function)

  fun tabTitleContains(text: String, function: TabTitleFixture.() -> Unit = {}): TabTitleFixture {
    return findElement<TabTitleFixture>(byXpath("tab with title $text", "//div[@class='ContentTabLabel' and contains(@text, '$text')]")).apply(function)
  }

  fun content(classType: String, function: TabContentFixture.() -> Unit = {}) =
    findElement<TabContentFixture>(byXpath("tab with content of type $classType", "//div[@class='$classType']")).apply(function)

  fun findCard(classType: String) =
    findElement<ComponentFixture>(byXpath("card with type $classType", "//div[@class='$classType']"))

  fun ensureOpen() {
    stripeButton(title).open()
  }

}
