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
package org.sonarlint.intellij.common.ui

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

class ReadActionUtils {
    companion object {
        @JvmStatic
        fun runReadActionSafely(project: Project, action: Runnable) {
            if (!project.isDisposed) {
                return ReadAction.run<Exception> {
                    if (!project.isDisposed) action.run()
                }
            }
        }

        @JvmStatic
        fun <T> computeReadActionSafely(action: ThrowableComputable<T, out Exception>): T {
            return ReadAction.compute<T, Exception> {
                action.compute()
            }
        }

        @JvmStatic
        fun <T> computeReadActionSafely(project: Project, action: ThrowableComputable<T, out Exception>): T? {
            if (!project.isDisposed) {
                return ReadAction.compute<T?, Exception> {
                    if (project.isDisposed) null else action.compute()
                }
            }
            return null
        }

        @JvmStatic
        fun <T> computeReadActionSafely(module: Module, action: ThrowableComputable<T, out Exception>): T? {
            if (!module.isDisposed) {
                return ReadAction.compute<T?, Exception> {
                    if (module.isDisposed) null else action.compute()
                }
            }
            return null
        }

        @JvmStatic
        fun <T> computeReadActionSafely(psiFile: PsiFile, action: ThrowableComputable<T, out Exception>): T? {
            return ReadAction.compute<T?, Exception> {
                if (!psiFile.isValid) null else action.compute()
            }
        }

        @JvmStatic
        fun <T> computeReadActionSafely(virtualFile: VirtualFile, project: Project, action: ThrowableComputable<T, out Exception>): T? {
            if (!project.isDisposed) {
                return ReadAction.compute<T, Exception> {
                    if (project.isDisposed || !virtualFile.isValid) null else action.compute()
                }
            }
            return null
        }

        @JvmStatic
        fun <T> computeReadActionSafelyInSmartMode(
            virtualFile: VirtualFile,
            project: Project,
            action: Computable<T>
        ): T? {
            if (!project.isDisposed && virtualFile.isValid) {
                return DumbService.getInstance(project).runReadActionInSmartMode(action)
            }
            return null
        }

        @JvmStatic
        fun <T> computeReadActionSafely(virtualFile: VirtualFile, action: ThrowableComputable<T, out Exception>): T? {
            return ReadAction.compute<T?, Exception> {
                if (!virtualFile.isValid) null else action.compute()
            }
        }
    }
}
