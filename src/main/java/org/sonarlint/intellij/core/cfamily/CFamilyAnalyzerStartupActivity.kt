/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
package org.sonarlint.intellij.core.cfamily

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

/**
 * Startup activity that checks CFamily analyzer availability on project open.
 *
 * When the analyzer is downloaded for the first time, it triggers a backend restart
 * to ensure the backend picks up the newly available analyzer.
 *
 * This runs once per IDE session to avoid redundant checks.
 */
class CFamilyAnalyzerStartupActivity : StartupActivity {

    private var hasRun = false
    private val lock = Any()

    override fun runActivity(project: Project) {

    }
}
