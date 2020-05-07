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
import com.intellij.remoterobot.fixtures.ActionLinkFixture
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.DefaultXpath
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.utils.keyboard
import java.io.File

fun RemoteRobot.welcomeFrame(function: WelcomeFrame.() -> Unit) {
  find(WelcomeFrame::class.java).apply(function)
}

@FixtureName("Welcome Frame")
@DefaultXpath("type", "//div[@class='FlatWelcomeFrame' and @visible='true']")
class WelcomeFrame(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent) {
  private val openOrImportLink
    get() = actionLink(ActionLinkFixture.byText("Open or Import"))

  private val legacyImportLink
    get() = actionLink(ActionLinkFixture.byText("Import Project"))

  fun importProject(projectName: String) {
    try {
      openOrImportLink.click()
    } catch (e: Exception) {
      // before 2020
      importProjectLegacy(projectName)
      return
    }
    dialog("Open File or Project") {
      keyboard { enterText(File("projects/$projectName").absolutePath) }
      button("OK").click()
    }
  }

  private fun importProjectLegacy(projectName: String) {
    legacyImportLink.click()
    dialog("Select File or Directory to Import") {
      keyboard { enterText(File("projects/$projectName/pom.xml").absolutePath) }
      button("OK").click()
    }
  }
}
