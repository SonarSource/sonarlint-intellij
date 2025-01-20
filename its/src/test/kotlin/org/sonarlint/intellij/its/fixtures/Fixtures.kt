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

import com.intellij.remoterobot.fixtures.ActionButtonFixture
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.Fixture
import com.intellij.remoterobot.fixtures.JRadioButtonFixture
import com.intellij.remoterobot.fixtures.JTextFieldFixture
import com.intellij.remoterobot.fixtures.JTreeFixture
import com.intellij.remoterobot.search.locators.Locator
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.stepsProcessing.step
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration
import org.assertj.swing.timing.Pause

inline fun <reified T : Fixture> ContainerFixture.findElement(locator: Locator): T {
    // the find has a 2s timeout and 2s interval by default, which sometimes allows only one search
    return find(locator, timeout = Duration.ofSeconds(5))
}

fun ContainerFixture.jbTextFields() = findAll<JTextFieldFixture>(byXpath("//div[@class='JBTextField']"))

fun ContainerFixture.jbTextFieldsWithBrowseButton() =
    findAll<JTextFieldFixture>(byXpath("//div[@class='TextFieldWithBrowseButton']"))

fun ContainerFixture.jRadioButtons() = findAll<JRadioButtonFixture>(byXpath("//div[@class='JRadioButton']"))

fun ContainerFixture.jbTextField() = findElement<JTextFieldFixture>(byXpath("//div[@class='JBTextField']"))

fun ContainerFixture.jbTable() = findElement<JBTableFixture>(byXpath("//div[@class='JBTable']"))

fun ContainerFixture.editorComponent() = findElement<ComponentFixture>(byXpath("//div[@class='PsiAwareTextEditorComponent']"))

fun CommonContainerFixture.jPasswordField() = textField(byXpath("//div[@class='JPasswordField']"))

fun CommonContainerFixture.jBPasswordField() = textField(byXpath("//div[@class='JBPasswordField']"))

fun ActionButtonFixture.clickWhenEnabled() {
  waitFor(Duration.ofSeconds(5)) {
    isEnabled()
  }
  click()
}

fun JTreeFixture.waitUntilLoaded() {
    step("waiting for loading text to go away...") {
        Pause.pause(100)
        waitFor(duration = Duration.ofMinutes(1)) {
            // Do not use hasText(String) https://github.com/JetBrains/intellij-ui-test-robot/issues/10
            !hasText { txt -> txt.text == "loading..." }
        }
        Pause.pause(100)
    }
}
