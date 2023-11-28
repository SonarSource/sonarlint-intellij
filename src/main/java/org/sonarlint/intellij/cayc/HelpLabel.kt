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
package org.sonarlint.intellij.cayc

import com.intellij.ide.BrowserUtil
import com.intellij.ui.ContextHelpLabel
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.documentation.SonarLintDocumentation
import org.sonarlint.intellij.telemetry.LinkTelemetry
import org.sonarlint.intellij.telemetry.SonarLintTelemetry

class HelpLabel {
    companion object {
        @JvmStatic
        fun createCleanAsYouCode() =
                ContextHelpLabel.createWithLink(null,
                        "Deliver clean code by focusing on code that was recently modified",
                        "Learn more about Clean as You Code", true) { BrowserUtil.browse(SonarLintDocumentation.FOCUS_ON_NEW_CODE_LINK) }

        @JvmStatic
        fun createConnectedMode() =
            ContextHelpLabel.createWithLink(null,
                "Unleash the full potential of SonarLint in Connected Mode with SonarQube or SonarCloud to analyze more languages, detect more issues, and receive notifications about the quality gate status.",
                "Learn more about Connected Mode", true) {
                SonarLintUtils.getService(SonarLintTelemetry::class.java).addQuickFixAppliedForRule(LinkTelemetry.CONNECTED_MODE_DOCS.linkId)
                BrowserUtil.browse(LinkTelemetry.CONNECTED_MODE_DOCS.url)
            }

    }
}
