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
package org.sonarlint.intellij.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project

class UiUtils {
    companion object {
        @JvmStatic
        fun runOnUiThread(modality: ModalityState, runnable: Runnable) {
            ApplicationManager.getApplication().invokeLater({
                runnable.run()
            }, modality)
        }

        @JvmStatic
        fun runOnUiThread(project: Project, runnable: Runnable) {
            runOnUiThread(project, ModalityState.defaultModalityState(), runnable)
        }

        @JvmStatic
        fun runOnUiThread(project: Project, modality: ModalityState, runnable: Runnable) {
            ApplicationManager.getApplication().invokeLater({
                if (!project.isDisposed) {
                    runnable.run()
                }
            }, modality)
        }

        @JvmStatic
        fun runOnUiThreadAndWait(project: Project, runnable: Runnable) {
            ApplicationManager.getApplication().invokeAndWait {
                if (!project.isDisposed) {
                    runnable.run()
                }
            }
        }
    }
}
