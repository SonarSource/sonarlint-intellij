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
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import java.time.Duration
import org.sonarlint.intellij.its.fixtures.tool.window.TabContentFixture

fun RemoteRobot.notification(
    title: String,
    timeout: Duration = Duration.ofSeconds(20),
    function: NotificationFixture.() -> Unit = {},
): NotificationFixture = step("Search for dialog with title $title") {
    val notification = find<NotificationFixture>(NotificationFixture.byTitle(title), timeout)
    notification.apply(function)
}

fun RemoteRobot.notificationByActionName(
    actionName: String,
    timeout: Duration = Duration.ofSeconds(20),
    function: NotificationFixture.() -> Unit = {},
): NotificationFixture = step("Search for dialog with action name $actionName") {
    val notification = find<NotificationFixture>(NotificationFixture.byAction(actionName), timeout)
    notification.apply(function)
}

fun RemoteRobot.firstNotification(
    timeout: Duration = Duration.ofSeconds(20),
    function: NotificationFixture.() -> Unit = {},
): NotificationFixture = step("Search first notification") {
    val notification = find<NotificationFixture>(NotificationFixture.first(), timeout)
    notification.apply(function)
}

@FixtureName("Notification")
open class NotificationFixture(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent,
) : CommonContainerFixture(remoteRobot, remoteComponent) {

    companion object {
        fun byTitle(title: String) = byXpath("//div[@accessiblename='$title']")
        fun byAction(action: String) = byXpath("//div[contains(@actionlinks, 'show')]//div[@text='Use configuration']")
        fun first() = byXpath("//div[@class='NotificationCenterPanel']//div[@class='JEditorPane']")
    }

    fun content(classType: String, function: TabContentFixture.() -> Unit = {}) =
        findElement<TabContentFixture>(byXpath("//div[@class='$classType']")).apply(function)

}
