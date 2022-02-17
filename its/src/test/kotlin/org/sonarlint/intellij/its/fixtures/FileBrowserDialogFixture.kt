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
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import java.nio.file.Paths
import java.time.Duration

fun RemoteRobot.fileBrowserDialog(
  possibleTitles: Array<String>,
  timeout: Duration = Duration.ofSeconds(20),
  function: FileBrowserDialogFixture.() -> Unit = {}): FileBrowserDialogFixture = step("Search for file browser dialog with title containing $possibleTitles") {
  find<FileBrowserDialogFixture>(DialogFixture.byPossibleTitles(possibleTitles), timeout).apply(function)
}

fun RemoteRobot.openProjectFileBrowserDialog(function: FileBrowserDialogFixture.() -> Unit = {}) = fileBrowserDialog(arrayOf(
  // 2020+
  "Open File or Project",
)).apply(function)


@FixtureName("File Browser Dialog")
class FileBrowserDialogFixture(
  remoteRobot: RemoteRobot,
  remoteComponent: RemoteComponent) : DialogFixture(remoteRobot, remoteComponent) {

  private val textField
    get() = textField(byXpath("//div[(@class='BorderlessTextField' or @class='JTextField')]"))

  private fun refreshButton(): ComponentFixture {
    return findElement(byXpath("//div[(@accessiblename='Refresh' and @class='ActionButton')]"))
  }

  fun selectProjectFile(projectName: String, isMaven: Boolean) {
    val projectsDir = Paths.get("build/projects").toAbsolutePath()
    val projectBaseDir = projectsDir.resolve(projectName)
    val projectFile = if (isMaven && remoteRobot.ideMajorVersion() < 201) projectBaseDir.resolve("pom.xml") else projectBaseDir
    val button = button("OK")
    waitFor { button.isEnabled() }

    // Select parent folder and refresh
    textField.text = projectsDir.normalize().toString()
    // it helps locating the project
    refreshButton().click()
    // Give some time for the refresh to work
    Thread.sleep(2000)

    textField.text = projectFile.normalize().toString()
    // Give some time for the refresh to work
    Thread.sleep(2000)
    button.click()
  }
}
