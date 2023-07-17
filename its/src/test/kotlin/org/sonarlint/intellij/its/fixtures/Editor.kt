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
package org.sonarlint.intellij.its.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import java.time.Duration

@JvmOverloads
fun ContainerFixture.editor(title: String, timeout: Duration = Duration.ofSeconds(20), function: Editor.() -> Unit = {}): ContainerFixture {
  find<ComponentFixture>(
    byXpath("//div[@class='EditorTabs']//div[@accessiblename='$title' and (@class='SingleHeightLabel' or @class='EditorTabLabel')]"), timeout).click()
  return find<Editor>(
    byXpath("title '$title'", "//div[@accessiblename='Editor for $title' and @class='EditorComponentImpl']"), timeout)
    .apply { runJs("robot.moveMouse(component);") }
    .apply(function)
}

@FixtureName("Editor")
class Editor(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent)
