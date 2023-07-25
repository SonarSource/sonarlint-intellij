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

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

class ReadActionUtils {
    companion object {
        fun <T> runReadActionSafely(project: Project, action: ThrowableComputable<T, out Exception>): T? {
            if (!project.isDisposed) {
                return ReadAction.compute(action)
            }
            return null
        }

        fun <T> runReadActionSafely(psiFile: PsiFile, action: ThrowableComputable<T, out Exception>): T? {
            if (psiFile.isValid) {
                return ReadAction.compute(action)
            }
            return null
        }

        fun <T> runReadActionSafely(virtualFile: VirtualFile, project: Project, action: ThrowableComputable<T, out Exception>): T? {
            if (virtualFile.isValid) {
                return runReadActionSafely(project, action)
            }
            return null
        }
    }
}
