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
package org.sonarlint.intellij.config.global.credentials

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.ArrayDeque
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException

internal class CredentialCancellation(val progress: ProgressIndicator? = null, private val deadlineNanos: Long = Long.MAX_VALUE) {
    private var cancelled = false
    private var worker: Thread? = null

    @Synchronized
    fun cancel() {
        cancelled = true
        progress?.cancel()
        worker?.interrupt()
    }

    @Synchronized
    fun checkCancelled() {
        failure()?.let { throw it }
    }

    @Synchronized
    fun failure(): Exception? = when {
        cancelled || progress?.isCanceled == true -> CancellationException()
        System.nanoTime() >= deadlineNanos -> TimeoutException("Credential operation timed out after 30 seconds.")
        else -> null
    }

    @Synchronized
    fun attachWorker() {
        checkCancelled()
        worker = Thread.currentThread()
    }

    @Synchronized
    fun detachWorker() {
        worker = null
        // Do not leak a request's interruption into the next queued operation.
        Thread.interrupted()
    }
}

/** A lane is held until the provider physically exits, including when it ignores cancellation. */
internal class CredentialOperationQueue {
    private val lanes = HashMap<String, ArrayDeque<Runnable>>()

    fun <T> submit(name: String, cancellation: CredentialCancellation, operation: () -> T): CompletableFuture<T> {
        val result = CompletableFuture<T>()
        val task = Runnable {
            try {
                cancellation.attachWorker()
                val progress = cancellation.progress
                if (progress == null) result.complete(operation())
                else ProgressManager.getInstance().executeProcessUnderProgress({ result.complete(operation()) }, progress)
            } catch (e: Exception) {
                result.completeExceptionally(e)
            } finally {
                cancellation.detachWorker()
                advance(name)
            }
        }
        synchronized(lanes) {
            val lane = lanes.getOrPut(name) { ArrayDeque() }
            lane.add(task)
            if (lane.size == 1) AppExecutorUtil.getAppExecutorService().execute(task)
        }
        return result
    }

    private fun advance(name: String) {
        synchronized(lanes) {
            val lane = lanes.getValue(name)
            lane.removeFirst()
            if (lane.isEmpty()) lanes.remove(name)
            else AppExecutorUtil.getAppExecutorService().execute(lane.first)
        }
    }
}
