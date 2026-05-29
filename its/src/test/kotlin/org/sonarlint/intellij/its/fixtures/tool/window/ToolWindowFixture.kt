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
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration
import org.sonarlint.intellij.its.fixtures.oldStripeButton

private val sonarLintToolWindowPaneLocator = byXpath(
    "SonarQube for IDE tool window",
    "//div[@class='ToolWindowPane'][.//div[@class='CurrentFilePanel' or @class='SonarLintLogPanel' or @class='SonarLintHelpAndFeedbackPanel']]"
)

fun ContainerFixture.toolWindow(
    title: String,
    function: ToolWindowFixture.() -> Unit = {},
): ToolWindowFixture = step("Search for tool window") {
    // Do not use the first ToolWindowPane in the frame (Git, Terminal, …): open our stripe and resolve the pane
    // that actually contains SonarQube for IDE content.
    waitFor(
        duration = Duration.ofMinutes(1),
        interval = Duration.ofMillis(500),
        errorMessage = "SonarQube for IDE tool window not found (no plugin content under ToolWindowPane)"
    ) {
        runCatching {
            toolWindowBar(title) { ensureOpen() }
        }
        runCatching {
            find<ToolWindowFixture>(Duration.ofSeconds(3)).apply {
                this.title = title
                ensureOpen()
            }
        }
        runCatching {
            find<ToolWindowFixture>(sonarLintToolWindowPaneLocator, Duration.ofSeconds(3))
        }.isSuccess
    }
    val toolWindowPane = find<ToolWindowFixture>(sonarLintToolWindowPaneLocator, Duration.ofSeconds(30))
    toolWindowPane.title = title
    toolWindowPane.apply(function)
}

fun ContainerFixture.toolWindow(
    function: ToolWindowFixture.() -> Unit = {},
): ToolWindowFixture = step("Search for tool window") {
    toolWindow("SonarQube for IDE", function)
}

@DefaultXpath(by = "ToolWindow type", xpath = "//div[@class='ToolWindowPane']")
@FixtureName(name = "Tool Window")
class ToolWindowFixture(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent) {
    lateinit var title: String

    fun tab(title: String, function: TabTitleFixture.() -> Unit = {}) =
        find<TabTitleFixture>(
            byXpath("tab with title $title", "//div[@class='ContentTabLabel' and @text='$title']"),
            Duration.ofSeconds(30)
        ).apply(function)

    fun tabTitleContains(text: String, function: TabTitleFixture.() -> Unit = {}): TabTitleFixture {
        return find<TabTitleFixture>(
            byXpath(
                "tab with title $text",
                "//div[@class='ContentTabLabel' and contains(@text, '$text')]"
            ),
            Duration.ofSeconds(30)
        ).apply(function)
    }

    fun content(classType: String, function: TabContentFixture.() -> Unit = {}) =
        find<TabContentFixture>(
            byXpath("tab with content of type $classType", "//div[@class='$classType']"),
            Duration.ofSeconds(30)
        ).apply(function)

    fun findCard(classType: String) =
        find<ComponentFixture>(
            byXpath("card with type $classType", "//div[@class='$classType']"),
            Duration.ofSeconds(30)
        )

    fun ensureOpen() {
        oldStripeButton(title).open()
    }

}
