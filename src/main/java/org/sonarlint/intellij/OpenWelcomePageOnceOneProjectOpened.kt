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
package org.sonarlint.intellij

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.sonarlint.intellij.common.util.SonarLintUtils
import org.sonarlint.intellij.config.Settings.getGlobalSettings
import org.sonarlint.intellij.ui.walkthrough.SonarLintWalkthroughToolWindow

private const val HAS_WALKTHROUGH_RUN_ONCE: String = "hasWalkthroughRunOnce"

class OpenWelcomePageOnceOneProjectOpened : StartupActivity {

    override fun runActivity(project: Project) {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            return
        }

        val properties = PropertiesComponent.getInstance()

        if (!properties.getBoolean(HAS_WALKTHROUGH_RUN_ONCE, false) && !getGlobalSettings().hasWalkthroughRunOnce()) {
            SonarLintUtils.getService(project, SonarLintWalkthroughToolWindow::class.java).openWelcomePage()
        }

        properties.setValue(HAS_WALKTHROUGH_RUN_ONCE, true)
        getGlobalSettings().setHasWalkthroughRunOnce(true)
    }

}
