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
package org.sonarlint.intellij.common.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.NonBlockingReadAction
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import java.util.concurrent.Callable

/**
 * Thread-aware read-action helpers for PSI/VFS access.
 *
 * Long-running read actions on a background thread must be cancellable: otherwise they can
 * prevent the EDT from writing and freeze the UI. This class picks the appropriate API
 * based on the current thread.
 *
 * - **Background thread** → [ReadAction.nonBlocking] (cancellable; expires when the project is disposed)
 * - **EDT** → [ReadAction.compute] / [ReadAction.run] ([ReadAction.nonBlocking] must not run on EDT)
 *
 * On background threads, cancellation or project expiration returns `null` (compute) or no-ops (run)
 * instead of throwing [ProcessCanceledException]. Non-blocking read actions may re-run their lambda
 * after cancellation; keep read-action bodies idempotent and free of non-repeatable side effects.
 *
 * Prefer these methods over calling [ReadAction] directly.
 */
class ReadActionUtils {
    companion object {
        @JvmStatic
        fun runReadActionSafely(project: Project, action: Runnable) {
            if (!project.isDisposed) {
                runCancellableReadAction(project) {
                    if (!project.isDisposed) action.run()
                }
            }
        }

        @JvmStatic
        fun runReadActionSafely(action: Runnable) {
            runCancellableReadAction {
                action.run()
            }
        }

        @JvmStatic
        fun <T> computeReadActionSafely(action: ThrowableComputable<T, out Exception>): T? {
            return computeCancellableReadAction { action.compute() }
        }

        @JvmStatic
        fun <T> computeReadActionSafely(project: Project, action: ThrowableComputable<T, out Exception>): T? {
            if (!project.isDisposed) {
                return computeCancellableReadAction(project) {
                    if (project.isDisposed) null else action.compute()
                }
            }
            return null
        }

        @JvmStatic
        fun <T> computeReadActionSafely(module: Module, action: ThrowableComputable<T, out Exception>): T? {
            if (!module.isDisposed) {
                return computeCancellableReadAction(module.project) {
                    if (module.isDisposed) null else action.compute()
                }
            }
            return null
        }

        @JvmStatic
        fun <T> computeReadActionSafely(psiFile: PsiFile, action: ThrowableComputable<T, out Exception>): T? {
            return computeCancellableReadAction(psiFile.project) {
                if (!psiFile.isValid) null else action.compute()
            }
        }

        @JvmStatic
        fun <T> computeReadActionSafely(virtualFile: VirtualFile, project: Project, action: ThrowableComputable<T, out Exception>): T? {
            if (!project.isDisposed) {
                return computeCancellableReadAction(project) {
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
                return if (ApplicationManager.getApplication().isDispatchThread) {
                    DumbService.getInstance(project).runReadActionInSmartMode(action)
                } else {
                    executeNonBlockingReadAction(
                        ReadAction.nonBlocking(Callable { action.compute() })
                            .inSmartMode(project)
                            .expireWhen { project.isDisposed || !virtualFile.isValid }
                    )
                }
            }
            return null
        }

        @JvmStatic
        fun <T> computeReadActionSafely(virtualFile: VirtualFile, action: ThrowableComputable<T, out Exception>): T? {
            return computeCancellableReadAction {
                if (!virtualFile.isValid) null else action.compute()
            }
        }

        private fun runCancellableReadAction(project: Project? = null, action: Runnable) {
            if (ApplicationManager.getApplication().isDispatchThread) {
                ReadAction.run<Exception> { action.run() }
            } else {
                var readAction = ReadAction.nonBlocking(Callable {
                    action.run()
                    null
                })
                if (project != null) {
                    readAction = readAction.expireWhen { project.isDisposed }
                }
                executeNonBlockingReadAction(readAction)
            }
        }

        private fun <T> computeCancellableReadAction(
            projectForExpiration: Project? = null,
            action: ThrowableComputable<T?, out Exception>,
        ): T? {
            if (ApplicationManager.getApplication().isDispatchThread) {
                return ReadAction.compute<T?, Exception> { action.compute() }
            }
            var readAction = ReadAction.nonBlocking(Callable { action.compute() })
            if (projectForExpiration != null) {
                readAction = readAction.expireWhen { projectForExpiration.isDisposed }
            }
            return executeNonBlockingReadAction(readAction)
        }

        /**
         * Runs a non-blocking read action on a background thread.
         * Returns null when the action is cancelled or expired — intentional boundary for *Safely* helpers.
         */
        private fun <T> executeNonBlockingReadAction(readAction: NonBlockingReadAction<T>): T? {
            return try {
                readAction.executeSynchronously()
            } catch (_: ProcessCanceledException) {
                null
            }
        }
    }
}
