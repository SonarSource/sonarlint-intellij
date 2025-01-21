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
package org.sonarlint.intellij.cayc

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.Locale
import org.sonarlint.intellij.common.util.SonarLintUtils.getService
import org.sonarlint.intellij.core.BackendService
import org.sonarlint.intellij.util.runOnPooledThread

@Service(Service.Level.PROJECT)
class NewCodePeriodCache(private val project: Project) {
    var periodAsString: String = "(unknown code period)"

    fun refreshAsync() {
        runOnPooledThread(project) {
            getService(BackendService::class.java).getNewCodePeriodText(project)
                .thenAccept { period ->
                    periodAsString = period.replaceFirstChar { char ->
                        char.lowercase(
                            Locale.getDefault()
                        )
                    }
                }
        }
    }
}
