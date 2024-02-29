/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2024 SonarSource
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
package org.sonarlint.intellij.dogfood

import com.intellij.openapi.actionSystem.AnActionEvent
import java.io.PrintWriter
import java.nio.file.Paths
import org.sonarlint.intellij.actions.AbstractSonarAction
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread

class DogfoodSetCredentialsAction : AbstractSonarAction("Set Credentials") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        runOnUiThread(project) {
            val credentials = DogfoodDialog().setCredentials()
            if (credentials != null) {
                val dogfoodPropertiesPath = getSonarlintSystemPath().resolve("dogfood.properties")
                val dogfoodPropertiesFile = dogfoodPropertiesPath.toFile()
                if (!dogfoodPropertiesFile.exists()) {
                    dogfoodPropertiesFile.createNewFile()
                }
                PrintWriter(dogfoodPropertiesFile).use {
                    it.println("username=${credentials.username}")
                    it.println("password=${credentials.password}")
                }
                loadCredentials()
                resetTries()
            }
        }
    }

    private fun getSonarlintSystemPath() = Paths.get(System.getProperty("user.home")).resolve(".sonarlint")

}