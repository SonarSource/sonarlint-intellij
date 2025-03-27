/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource
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
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.attempt
import java.time.Duration

@FixtureName("Action Menu")
class ActionMenuFixture(
  remoteRobot: RemoteRobot,
  remoteComponent: RemoteComponent
) : CommonContainerFixture(remoteRobot, remoteComponent) {

  fun open() {
    // sometimes the menu list does not appear after the click
    attempt(tries = 3) {
      click()
      find<ContainerFixture>(byXpath("//div[@class='JBPopupMenu']"), timeout = Duration.ofSeconds(1))
    }
  }

  fun item(label: String, function: ActionMenuItemFixture.() -> Unit = {}): ActionMenuItemFixture {
    return if (remoteRobot.isModernUI()) {
      findElement<ActionMenuItemFixture>(byXpath("//div[@text='$label']")).apply(
        function
      )
    } else {
      findElement<ActionMenuItemFixture>(byXpath("menu item $label", "//div[@class='ActionMenuItem' and @text='$label']")).apply(
        function
      )
    }
  }
}
