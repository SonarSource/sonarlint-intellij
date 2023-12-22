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
package org.sonarlint.intellij.util

import com.intellij.ide.BrowserUtil
import com.intellij.ui.ContextHelpLabel
import org.sonarlint.intellij.documentation.SonarLintDocumentation.Intellij.FOCUS_ON_NEW_CODE_LINK
import org.sonarlint.intellij.telemetry.LinkTelemetry

class HelpLabelUtils {
    companion object {
        @JvmStatic
        fun createHelpTextNotConnected(helpText: String) =
                ContextHelpLabel.createWithLink(null,
                        helpText +
                            "SonarCloud complements SonarLint by detecting more across the whole project.",
                        "Try SonarCloud for free", true) {
                    LinkTelemetry.SONARCLOUD_SIGNUP_PAGE.browseWithTelemetry()
                }

        @JvmStatic
        fun createHelpText(helpText: String) =
            ContextHelpLabel.create(helpText)

        @JvmStatic
        fun createCleanAsYouCode() =
            ContextHelpLabel.createWithLink(null,
                "Deliver clean code by focusing on code that was recently modified",
                "Learn more about Clean as You Code", true) { BrowserUtil.browse(FOCUS_ON_NEW_CODE_LINK) }

        @JvmStatic
        fun createConnectedMode() =
            ContextHelpLabel.createWithLink(null,
                "Connected Mode links SonarLint with SonarCloud or SonarQube to analyze more languages, detect more issues, and receive notifications about the quality gate status.",
                "Learn more about Connected Mode", true) {
                LinkTelemetry.CONNECTED_MODE_DOCS.browseWithTelemetry()
            }

    }
}
