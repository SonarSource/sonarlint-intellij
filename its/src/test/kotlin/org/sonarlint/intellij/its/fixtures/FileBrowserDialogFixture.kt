/*
 * SonarLint for IntelliJ IDEA ITs
 * Copyright (C) 2015-2020 SonarSource
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
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import java.nio.file.Path
import java.time.Duration

fun ContainerFixture.fileBrowserDialog(
  possibleTitles: Array<String>,
  timeout: Duration = Duration.ofSeconds(20),
  function: FileBrowserDialogFixture.() -> Unit = {}): FileBrowserDialogFixture = step("Search for file browser dialog with title containing $possibleTitles") {
  find<FileBrowserDialogFixture>(DialogFixture.byPossibleTitles(possibleTitles), timeout).apply(function)
}

@FixtureName("File Browser Dialog")
class FileBrowserDialogFixture(
  remoteRobot: RemoteRobot,
  remoteComponent: RemoteComponent) : DialogFixture(remoteRobot, remoteComponent) {

  private val textField
    get() = textField(byXpath("//div[(@class='BorderlessTextField' or @class='JTextField')]"))

  private fun actionButton(text: String): ComponentFixture {
    return find(byXpath("//div[(@accessiblename='$text' and @class='ActionButton')]"))
  }

  fun selectFile(filePath: Path) {
    val button = button("OK")
    waitFor { button.isEnabled() }
    textField.text = filePath.toAbsolutePath().normalize().toString()
    // it helps locating the project
    actionButton("Refresh").click()
    button.click()
  }
}