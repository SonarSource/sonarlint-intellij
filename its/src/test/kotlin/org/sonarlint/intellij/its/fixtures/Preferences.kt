/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
import com.intellij.remoterobot.fixtures.ActionButtonFixture
import com.intellij.remoterobot.fixtures.FixtureName
import com.intellij.remoterobot.fixtures.JLabelFixture
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import java.time.Duration

fun RemoteRobot.preferencesDialog(
    timeout: Duration = Duration.ofSeconds(20),
    function: PreferencesDialog.() -> Unit
) {
    step("Search for preferences dialog") {
        val dialog = find<PreferencesDialog>(DialogFixture.byTitleContains(preferencesTitle()), timeout)

        dialog.apply(function)

        if (dialog.isShowing) {
            dialog.close()
        }
    }
}

@FixtureName("Preferences")
open class PreferencesDialog(
    remoteRobot: RemoteRobot,
    remoteComponent: RemoteComponent
) : DialogFixture(remoteRobot, remoteComponent) {
    fun search(query: String) = step("Search $query") {
        textField(byXpath("//div[@class='SettingsSearch']//div[@class='TextFieldWithProcessing']")).text = query
    }

    fun searchRule(query: String) = step("Search $query") {
        textField(byXpath("//div[@class='SearchTextField']//div[@class='TextFieldWithProcessing']")).text = query
    }

    fun tree(function: JTreeFixture.() -> Unit) {
        findElement<JTreeFixture>(byXpath("//div[@class='MyTree']")).apply(function)
    }

    fun errorMessage(message: String) {
        jLabel(JLabelFixture.byContainsText(message))
    }

    fun removeConnectionButton() =
        findElement<ActionButtonFixture>(byXpath("remove connection action button", "//div[@myicon='remove.svg']"))

    fun addConnectionButton() =
        findElement<ActionButtonFixture>(byXpath("add connection action button", "//div[@myicon='add.svg']"))
}

fun RemoteRobot.preferencesTitle() = if (this.isMac()) "Preferences" else "Settings"
