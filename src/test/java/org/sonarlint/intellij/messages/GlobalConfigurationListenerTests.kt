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
package org.sonarlint.intellij.messages

import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.sonarlint.intellij.AbstractSonarLintLightTests
import org.sonarlint.intellij.config.global.ServerConnection
import org.sonarlint.intellij.config.global.SonarLintGlobalSettings
import org.sonarlint.intellij.fixtures.newSonarQubeConnection

internal class GlobalConfigurationListenerTests : AbstractSonarLintLightTests() {
    private val testList: MutableList<ServerConnection> = LinkedList()

    @BeforeEach
    fun prepare() {
        testList.add(newSonarQubeConnection("name"))
    }

    @Test
    fun testChanged() {
        val servers: MutableList<ServerConnection> = LinkedList()
        val listener: GlobalConfigurationListener = object : GlobalConfigurationListener.Adapter() {
            override fun changed(serverList: List<ServerConnection>) {
                servers.addAll(serverList)
            }
        }

        project.messageBus.connect().subscribe(GlobalConfigurationListener.TOPIC, listener)
        project.messageBus.syncPublisher(GlobalConfigurationListener.TOPIC).changed(testList)
        Assertions.assertThat(servers).isEqualTo(testList)
    }

    @Test
    fun testApplied() {
        val isAutoTrigger = AtomicBoolean(false)

        val listener: GlobalConfigurationListener = object : GlobalConfigurationListener.Adapter() {
            override fun applied(previousSettings: SonarLintGlobalSettings, newSettings: SonarLintGlobalSettings) {
                isAutoTrigger.set(newSettings.isAutoTrigger)
            }
        }

        project.messageBus.connect().subscribe(GlobalConfigurationListener.TOPIC, listener)
        val settings = SonarLintGlobalSettings()
        settings.isAutoTrigger = true
        project.messageBus.syncPublisher(GlobalConfigurationListener.TOPIC).applied(SonarLintGlobalSettings(), settings)

        Assertions.assertThat(isAutoTrigger.get()).isTrue()
    }
}
