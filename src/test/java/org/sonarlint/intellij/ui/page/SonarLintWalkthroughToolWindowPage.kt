/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) SonarSource Sàrl
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
package org.sonarlint.intellij.ui.page

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import org.sonarlint.intellij.fixtures.HeadlessToolWindowHarness
import org.sonarlint.intellij.ui.icons.SonarLintIcons
import org.sonarlint.intellij.ui.walkthrough.SonarLintWalkthroughToolWindowFactory
import org.sonarlint.intellij.ui.walkthrough.WalkthroughPanel

const val WALKTHROUGH_TOOL_WINDOW_ID: String = "Welcome to SonarQube for IDE"

class SonarLintWalkthroughToolWindowPage private constructor(
    private val harness: HeadlessToolWindowHarness,
) {

    val hasWalkthroughPanel: Boolean
        get() = harness.registerToolWindowIfAbsent(
            WALKTHROUGH_TOOL_WINDOW_ID,
            SonarLintWalkthroughToolWindowFactory(),
            SonarLintIcons.WALKTHROUGH_TOOLWINDOW_ICON,
            ToolWindowAnchor.LEFT,
        ).let { toolWindow ->
            harness.ensureFactoryContentCreated(
                toolWindow,
                SonarLintWalkthroughToolWindowFactory(),
                hasContent = { tw -> tw.contentManager.contentCount > 0 },
            )
            toolWindow.contentManager.contentCount == 1 &&
                toolWindow.contentManager.contents[0].component is WalkthroughPanel
        }

    fun close() {
        harness.teardownLightUi(WALKTHROUGH_TOOL_WINDOW_ID)
    }

    companion object {
        fun open(project: Project): SonarLintWalkthroughToolWindowPage {
            val harness = HeadlessToolWindowHarness(project)
            val page = SonarLintWalkthroughToolWindowPage(harness)
            page.hasWalkthroughPanel
            return page
        }
    }
}
