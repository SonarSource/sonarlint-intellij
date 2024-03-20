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
package org.sonarlint.intellij.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.okCancel
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread


class ExportConfigurationAction : AbstractSonarAction("Share Configuration") {
    override fun actionPerformed(e: AnActionEvent) {
        e.project ?: return

        runOnUiThread(e.project!!) {
            if (confirm(e.project)) {
                println("Confirmed to share")
            }
        }
    }

    companion object {
        fun confirm(project: Project?): Boolean {
            return okCancel(
                "Share this Connected Mode configuration?",
                "A configuration file will be created in this working directory, making it easier for other team members to configure the binding for the same project"
            )
                .yesText("Share configuration")
                .noText("Cancel")
                .ask(project)
        }
    }
}

