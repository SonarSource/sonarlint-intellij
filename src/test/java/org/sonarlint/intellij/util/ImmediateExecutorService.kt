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
package org.sonarlint.intellij.util

import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class ImmediateExecutorService : ExecutorService {
    override fun execute(command: Runnable) {
        command.run()
    }

    override fun shutdown() {
    }

    override fun shutdownNow() = emptyList<Runnable>()

    override fun isShutdown() = false

    override fun isTerminated() = false

    override fun awaitTermination(timeout: Long, unit: TimeUnit) = true

    override fun <T : Any?> submit(task: Callable<T>) =
        CompletableFuture.completedFuture(task.call())

    override fun <T : Any?> submit(task: Runnable, result: T): Future<T> {
        task.run()
        return CompletableFuture.completedFuture(result)
    }

    override fun submit(task: Runnable): Future<*> {
        task.run()
        return CompletableFuture.completedFuture(null)
    }

    override fun <T : Any?> invokeAll(tasks: MutableCollection<out Callable<T>>) = emptyList<Future<T>>()

    override fun <T : Any?> invokeAll(
        tasks: MutableCollection<out Callable<T>>,
        timeout: Long,
        unit: TimeUnit
    ) = emptyList<Future<T>>()

    override fun <T : Any?> invokeAny(tasks: MutableCollection<out Callable<T>>) = null

    override fun <T : Any?> invokeAny(tasks: MutableCollection<out Callable<T>>, timeout: Long, unit: TimeUnit) = null

}
