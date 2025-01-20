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
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.DefaultXpath
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.log
import com.intellij.remoterobot.stepsProcessing.step
import java.time.Duration

fun RemoteRobot.welcomeFrame(function: WelcomeFrame.() -> Unit) {
    find(WelcomeFrame::class.java, byXpath("//div[@class='FlatWelcomeFrame']"), Duration.ofSeconds(7)).apply(function)
}

@FixtureName("Welcome Frame")
@DefaultXpath("type", "//div[@class='FlatWelcomeFrame']")
class WelcomeFrame(remoteRobot: RemoteRobot, remoteComponent: RemoteComponent) : CommonContainerFixture(remoteRobot, remoteComponent) {

    fun openProjectButton(): ComponentFixture {
        selectTab("Projects")
        // This can match two things: If no previous projects, its a SVG icon, else a jbutton
        return findAll<ComponentFixture>(byXpath("//div[contains(@accessiblename, 'Open') and (@class='MainButton' or @class='JButton')]")).first()
    }

    fun openPreferences() = step("Opening preferences dialog") {
        selectTab("Customize")
        findElement<ComponentFixture>(byXpath("//div[@accessiblename='All settings\u2026']")).click()
        log.info("Successfully opened the preferences dialog")
    }

    private fun selectTab(tabName: String) {
        findElement<ComponentFixture>(byXpath("//div[@accessiblename='Welcome screen categories']")).findText(tabName).click()
    }

}
