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
package org.sonarlint.intellij.finding.sca

import com.intellij.ide.ActivityTracker
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.actions.SonarLintToolWindow
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.ui.UiUtils.Companion.runOnUiThread

@Service(Service.Level.PROJECT)
class ScaAnalysisStatus(private val project: Project) {
    @Volatile
    var running = false
        private set

    fun setRunningState(value: Boolean) {
        running = value
        runOnUiThread(project) {
            getService(project, SonarLintToolWindow::class.java).refreshViews()
            // The running state can flip outside any user interaction (backend status notification, or the analysis
            // future completing on a pooled thread), so the toolbar would not otherwise re-evaluate the
            // Analyze <-> Stop toggle. Bumping the activity counter forces visible toolbars and menus to re-run
            // their action update(), making the cancel button appear/disappear immediately.
            ActivityTracker.getInstance().inc()
        }
    }
}
