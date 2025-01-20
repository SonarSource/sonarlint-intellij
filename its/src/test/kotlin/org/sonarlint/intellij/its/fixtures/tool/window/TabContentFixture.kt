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
package org.sonarlint.intellij.its.fixtures.tool.window

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.ActionButtonFixture
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.EditorFixture
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import org.sonarlint.intellij.its.fixtures.findElement

@FixtureName(name = "Tool Window Tab Content")
class TabContentFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent) {

  fun console() = findElement<EditorFixture>(EditorFixture.locator)
  fun toolBarButton(name: String) =
    findElement<ActionButtonFixture>(byXpath("finding action button with text '$name'", "//div[@accessiblename='$name']"))
  fun focusOnNewCodeButton() =
    findElement<ActionButtonFixture>(byXpath("focus on new code action button", "//div[@myicon='focus.svg']"))
  fun resolvedIssuesButton() =
    findElement<ActionButtonFixture>(byXpath("resolved issues action button", "//div[@myicon='resolved.svg']"))

}
