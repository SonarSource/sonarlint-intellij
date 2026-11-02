/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
import com.intellij.remoterobot.fixtures.JTextFieldFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import java.awt.Point
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

fun RemoteRobot.openSolutionBrowserDialog(function: FileBrowserDialogFixture.() -> Unit = {}) = fileBrowserDialog(
  arrayOf(
    "Select Path",
  )
).apply(function)


@FixtureName("File Browser Dialog")
class FileBrowserDialogFixture(
  remoteRobot: RemoteRobot,
  remoteComponent: RemoteComponent) : DialogFixture(remoteRobot, remoteComponent) {

  /** Up to 2026.1 the dialog always shows a path field. */
  private fun classicPathField(): JTextFieldFixture? =
    findAll<JTextFieldFixture>(byXpath("//div[(@class='BorderlessTextField' or @class='JTextField')]")).firstOrNull()

  /** From 2026.2 it shows a breadcrumb bar instead, which turns into a path field when its empty end is clicked. */
  private fun breadcrumbPathField(): JTextFieldFixture {
    val breadcrumbs = find<ComponentFixture>(byXpath("//div[@class='Breadcrumbs']"))
    breadcrumbs.click(Point(breadcrumbs.remoteComponent.width - 5, breadcrumbs.remoteComponent.height / 2))
    return find(byXpath("//div[@class='NioPathTextField']"), Duration.ofSeconds(5))
  }

  private fun refreshButton(): ComponentFixture {
    return findElement(byXpath("//div[(@accessiblename='Refresh' and @class='ActionButton')]"))
  }

  fun selectProjectFile(projectName: String) {
    val projectsDir = Paths.get("projects").toAbsolutePath()
    val projectBaseDir = projectsDir.resolve("$projectName-tmp")
    val button = button("OK")
    waitFor(Duration.ofSeconds(10)) { button.isEnabled() }

    val classicField = classicPathField()
    if (classicField != null) {
      // Select parent folder and refresh
      classicField.text = projectsDir.normalize().toString()
      // it helps locating the project
      refreshButton().click()
      // Give some time for the refresh to work
      Thread.sleep(2000)

      classicField.text = projectBaseDir.normalize().toString()
    } else {
      val pathField = breadcrumbPathField()
      pathField.text = projectBaseDir.normalize().toString()
      // The path is applied on Enter, which can also confirm the dialog
      pathField.click()
      remoteRobot.keyboard { enter() }
    }
    // Give some time for the refresh to work
    Thread.sleep(2000)
    // Enter may already have closed the dialog
    if (runCatching { isShowing }.getOrDefault(false)) {
      button.click()
    }
  }
}
