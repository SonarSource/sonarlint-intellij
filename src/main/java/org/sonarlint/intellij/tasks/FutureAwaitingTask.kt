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
package org.sonarlint.intellij.tasks

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

open class FutureAwaitingTask<T>(
    project: Project,
    title: String,
    private val future: CompletableFuture<T>,
) :
    Task.WithResult<T, Exception>(project, title, true) {
    override fun compute(indicator: ProgressIndicator): T? {
        return waitForFuture(indicator, future)
    }

    private fun waitForFuture(indicator: ProgressIndicator, future: CompletableFuture<T>): T? {
        while (true) {
            if (indicator.isCanceled) {
                future.cancel(true)
                return null
            }
            return try {
                future.get(100, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                continue
            } catch (_: InterruptedException) {
                throw InterruptedException("Interrupted")
            } catch (_: CancellationException) {
                throw InterruptedException("Operation cancelled")
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            }
        }
    }
}
