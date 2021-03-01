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
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.Locator
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import java.time.Duration

fun ContainerFixture.dialog(
  title: String,
  timeout: Duration = Duration.ofSeconds(20),
  function: DialogFixture.() -> Unit = {}): DialogFixture = step("Search for dialog with title $title") {
  find<DialogFixture>(DialogFixture.byTitle(title), timeout).apply(function)
}

fun RemoteRobot.dialog(
  title: String,
  timeout: Duration = Duration.ofSeconds(20),
  function: DialogFixture.() -> Unit = {}): DialogFixture = step("Search for dialog with title $title") {
  find<DialogFixture>(DialogFixture.byTitle(title), timeout).apply(function)
}

@FixtureName("Dialog")
open class DialogFixture(
  remoteRobot: RemoteRobot,
  remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent) {

  companion object {
    @JvmStatic
    fun byTitle(title: String) = byXpath("title $title", "//div[@title='$title' and @class='MyDialog']")

    @JvmStatic
    fun byPossibleTitles(possibleTitles: Array<String>): Locator {
      val titles = possibleTitles.joinToString(" or ")
      return byXpath("title part $titles",
        "//div[@class='MyDialog' and (${or("title", possibleTitles)})]")
    }

    fun or(attribute: String, values: Array<String>) = values.joinToString(" or ") { "@$attribute='$it'" }
  }
}
