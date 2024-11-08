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

import com.intellij.ui.ContextHelpLabel
import org.sonarlint.intellij.telemetry.LinkTelemetry

class HelpLabelUtils {
    companion object {
        @JvmStatic
        fun createHelpTextNotConnected(helpText: String) =
                ContextHelpLabel.createWithLink(null,
                        helpText +
                            "SonarQube Cloud complements SonarQube for IntelliJ by detecting more across the whole project.",
                    "Try SonarQube Cloud for free", true
                ) {
                    LinkTelemetry.SONARCLOUD_SIGNUP_PAGE.browseWithTelemetry()
                }

        @JvmStatic
        fun createHelpText(helpText: String) =
            ContextHelpLabel.create(helpText)

        @JvmStatic
        fun createCleanAsYouCode() =
            ContextHelpLabel.create(
                "<html>Use Connected Mode to benefit from an<br>accurate new code definition based on your SonarQube (Server, Cloud) settings." +
                    "<br>" +
                    "<br>Without Connected Mode, any code added or changed in the last 30 days is considered new code.</html>"
            )

        @JvmStatic
        fun createConnectedMode() =
            ContextHelpLabel.createWithLink(null,
                "Connected Mode links SonarQube for IntelliJ with SonarQube (Server, Cloud) to analyze more languages, detect more issues, and receive notifications about the quality gate status.",
                "Learn more about Connected Mode", true) {
                LinkTelemetry.CONNECTED_MODE_DOCS.browseWithTelemetry()
            }

    }
}
