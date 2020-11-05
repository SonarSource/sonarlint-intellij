/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2020 SonarSource
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
package org.sonarlint.intellij.server

import com.intellij.openapi.ui.Messages
import org.sonarlint.intellij.config.global.wizard.NewConnectionWizard
import org.sonarlint.intellij.issue.hotspot.SecurityHotspotOpener
import org.sonarlint.intellij.issue.hotspot.SecurityHotspotOpeningResult

open class SecurityHotspotOrchestrator(private val opener: SecurityHotspotOpener = SecurityHotspotOpener()) {

    open fun open(projectKey: String, hotspotKey: String, serverUrl: String) {
        do {
            val result = opener.open(projectKey, hotspotKey, serverUrl)
            val shouldRetry = handleOpeningResult(result, serverUrl)
        } while (shouldRetry)
    }

    private fun handleOpeningResult(result: SecurityHotspotOpeningResult, serverUrl: String): Boolean {
        when (result) {
            SecurityHotspotOpeningResult.NO_MATCHING_CONNECTION -> {
                val message = "There is no connection configured to $serverUrl."
                return Notifier.showYesNoModalWindow(message, "Create connection") {
                    return@showYesNoModalWindow NewConnectionWizard().open(serverUrl)
                }
            }
        }
        return false
    }
}

object Notifier {

    fun showYesNoModalWindow(message: String, yesText: String, callback: () -> Boolean): Boolean {
        val result = Messages.showYesNoDialog(null, message, "Couldn't open security hotspot", yesText, "Cancel", Messages.getWarningIcon())
        if (result == Messages.OK) {
            return callback()
        }
        return false
    }
}
