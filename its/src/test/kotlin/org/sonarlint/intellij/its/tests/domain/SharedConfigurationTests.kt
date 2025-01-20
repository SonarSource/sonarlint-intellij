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
package org.sonarlint.intellij.its.tests.domain

import org.sonarlint.intellij.its.BaseUiTest.Companion.remoteRobot
import org.sonarlint.intellij.its.fixtures.dialog
import org.sonarlint.intellij.its.fixtures.idea
import org.sonarlint.intellij.its.fixtures.jBPasswordField
import org.sonarlint.intellij.its.fixtures.notification
import org.sonarlint.intellij.its.fixtures.notificationByActionName

class SharedConfigurationTests {

    companion object {

        fun shareConfiguration() {
            with(remoteRobot) {
                idea {
                    notification("Share configuration").click()
                    dialog("Share This Connected Mode Configuration?") {
                        button("Share Configuration").click()
                    }
                }
            }
        }

        fun importConfiguration(token: String) {
            with(remoteRobot) {
                idea {
                    notificationByActionName("Use configuration").click()
                    dialog("Connect to This SonarQube Server Instance?") {
                        jBPasswordField().text = token
                        buttonContainsText("Connect to This SonarQube Server Instance").click()
                    }
                }
            }
        }

    }

}
