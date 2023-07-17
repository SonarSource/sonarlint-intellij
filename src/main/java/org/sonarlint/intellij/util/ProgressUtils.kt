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
package org.sonarlint.intellij.util

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object ProgressUtils {

    @JvmStatic
    fun <T> waitForFuture(indicator: ProgressIndicator, future: CompletableFuture<T>): T {
        while (true) {
            try {
                indicator.checkCanceled()
            } catch (e: ProcessCanceledException) {
                future.cancel(true)
                throw e
            }
            try {
                return future.get(100, TimeUnit.MILLISECONDS)
            } catch (t: TimeoutException) {
                continue
            } catch (e: InterruptedException) {
                throw ProcessCanceledException()
            } catch (e: CancellationException) {
                throw ProcessCanceledException()
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            }
        }
    }

}
