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
import com.intellij.remoterobot.fixtures.ActionLinkFixture
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.DefaultXpath
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import java.time.Duration

fun RemoteRobot.welcomeFrame(function: WelcomeFrame.() -> Unit) {
  find(WelcomeFrame::class.java, Duration.ofSeconds(5)).apply(function)
}

@FixtureName("Welcome Frame")
@DefaultXpath("type", "//div[@class='FlatWelcomeFrame']")
class WelcomeFrame(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent) {
  // from 203+
  private val newOpenButton
    get() = button(byXpath("//div[contains(@accessiblename, 'Open') and (@class='MainButton' or @class='JButton')]"))

  // from 201 up to 202
  private val openOrImportLink
    get() = actionLink(ActionLinkFixture.byText("Open or Import"))

  // up to 193 in IDEA
  private val legacyImportLinkIdea
    get() = actionLink(ActionLinkFixture.byText("Import Project"))

  // up to 193 in CLion
  private val legacyImportLinkCLion
    get() = actionLink(ActionLinkFixture.byText("Open"))

  fun openProjectButton(): ComponentFixture {
    val ideMajorVersion = remoteRobot.ideMajorVersion()
    val isCLion = remoteRobot.isCLion()
    return when {
      ideMajorVersion < 201 -> if (isCLion) legacyImportLinkCLion else legacyImportLinkIdea
      ideMajorVersion < 202 -> openOrImportLink
      else -> newOpenButton
    }
  }
}
