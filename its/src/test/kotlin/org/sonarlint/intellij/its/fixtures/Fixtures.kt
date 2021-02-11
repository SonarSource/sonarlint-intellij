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

import com.intellij.remoterobot.fixtures.ActionButtonFixture
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ContainerFixture
import com.intellij.remoterobot.fixtures.JRadioButtonFixture
import com.intellij.remoterobot.fixtures.JTextFieldFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration

fun ContainerFixture.jbTextFields() = findAll<JTextFieldFixture>(byXpath("//div[@class='JBTextField']"))

fun ContainerFixture.jRadioButtons() = findAll<JRadioButtonFixture>(byXpath("//div[@class='JRadioButton']"))

fun ContainerFixture.jbTextField() = find<JTextFieldFixture>(byXpath("//div[@class='JBTextField']"))

fun CommonContainerFixture.jTextField() = textField(byXpath("//div[@class='JTextField']"))

fun CommonContainerFixture.searchField() = textField(byXpath("//div[@class='SearchField']"))

fun ActionButtonFixture.clickWhenEnabled() {
  waitFor(Duration.ofSeconds(5)) {
    isEnabled()
  }
  click()
}