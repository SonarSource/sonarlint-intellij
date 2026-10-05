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
package org.sonarlint.intellij.trigger

import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EventSchedulerTests {

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `edits received while a batch is being sent form a later batch`(atInterval: Boolean) {
        val timer = ControlledTimer()
        val firstFile = mock<VirtualFile>()
        val laterFile = mock<VirtualFile>()
        val firstSend = BlockingCall()
        val batches = mutableListOf<Set<VirtualFile>>()
        val scheduler = EventScheduler(100, atInterval, timer.executor) { batch ->
            batches.add(batch)
            if (batches.size == 1) firstSend.block()
        }

        withWorkers { workers ->
            try {
                scheduler.notify(firstFile)
                timer.elapseBy(100)
                val firstCallback = workers.submit { assertThat(timer.runNext()).isTrue() }
                firstSend.awaitEntered()

                scheduler.notify(firstFile)
                scheduler.notify(laterFile)
                val replacement = timer.tasks.last()
                firstSend.release()
                firstCallback.get(5, TimeUnit.SECONDS)

                assertThat(batches).containsExactly(setOf(firstFile))
                assertThat(replacement.cancelled).isFalse()
                timer.elapseBy(100)
                assertThat(timer.runNext()).isTrue()
                assertThat(batches).containsExactly(setOf(firstFile), setOf(firstFile, laterFile))
                assertThat(timer.runNext()).isFalse()
            } finally {
                firstSend.release()
                scheduler.stopScheduler()
            }
        }
    }

    @Test
    fun `a cancelled debounce callback cannot drain files or clear its replacement`() {
        val timer = ControlledTimer()
        val batches = mutableListOf<Set<VirtualFile>>()
        val scheduler = EventScheduler(100, false, timer.executor, batches::add)
        val firstFile = mock<VirtualFile>()
        val secondFile = mock<VirtualFile>()
        try {
            scheduler.notify(firstFile)
            val staleCallback = timer.tasks.single()
            timer.elapseBy(50)
            scheduler.notify(secondFile)

            assertThat(staleCallback.cancelled).isTrue()
            assertThat(staleCallback.cancelledWithInterrupt).isFalse()
            staleCallback.callback.run()
            assertThat(batches).isEmpty()

            timer.elapseBy(100)
            assertThat(timer.runNext()).isTrue()
            staleCallback.callback.run()
            assertThat(batches).containsExactly(setOf(firstFile, secondFile))
        } finally {
            scheduler.stopScheduler()
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `interval keeps the first deadline and debounce postpones it`(atInterval: Boolean) {
        val timer = ControlledTimer()
        val batches = mutableListOf<Set<VirtualFile>>()
        val scheduler = EventScheduler(100, atInterval, timer.executor, batches::add)
        val firstFile = mock<VirtualFile>()
        val secondFile = mock<VirtualFile>()
        try {
            scheduler.notify(firstFile)
            timer.elapseBy(50)
            scheduler.notify(secondFile)
            scheduler.notify(secondFile)
            timer.elapseBy(50)

            assertThat(timer.runNext()).isEqualTo(atInterval)
            if (!atInterval) {
                assertThat(batches).isEmpty()
                timer.elapseBy(50)
                assertThat(timer.runNext()).isTrue()
            }
            assertThat(batches).containsExactly(setOf(firstFile, secondFile))
        } finally {
            scheduler.stopScheduler()
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `stop cancels pending work and ignores repeated stops and late edits`(atInterval: Boolean) {
        val timer = ControlledTimer()
        val batches = mutableListOf<Set<VirtualFile>>()
        val scheduler = EventScheduler(100, atInterval, timer.executor, batches::add)
        scheduler.notify(mock())
        val cancelledTask = timer.tasks.single()

        scheduler.stopScheduler()
        scheduler.stopScheduler()
        scheduler.notify(mock())
        cancelledTask.callback.run()

        assertThat(cancelledTask.cancelled).isTrue()
        assertThat(cancelledTask.cancelledWithInterrupt).isTrue()
        assertThat(timer.tasks).hasSize(1)
        assertThat(timer.shutdownCalls.get()).isEqualTo(1)
        assertThat(batches).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `stop waits for a concurrent notification to finish scheduling before cancelling it`(atInterval: Boolean) {
        val timer = ControlledTimer()
        val scheduling = BlockingCall()
        timer.beforeSchedule = scheduling::block
        val batches = mutableListOf<Set<VirtualFile>>()
        val scheduler = EventScheduler(100, atInterval, timer.executor, batches::add)

        withWorkers { workers ->
            try {
                val notification = workers.submit { scheduler.notify(mock()) }
                scheduling.awaitEntered()
                val stopStarted = CountDownLatch(1)
                val stopping = workers.submit {
                    stopStarted.countDown()
                    scheduler.stopScheduler()
                }
                assertThat(stopStarted.await(5, TimeUnit.SECONDS)).isTrue()
                scheduling.release()
                notification.get(5, TimeUnit.SECONDS)
                stopping.get(5, TimeUnit.SECONDS)
                scheduler.notify(mock())
                timer.tasks.single().callback.run()

                assertThat(timer.tasks.single().cancelled).isTrue()
                assertThat(timer.tasks).hasSize(1)
                assertThat(timer.shutdownCalls.get()).isEqualTo(1)
                assertThat(batches).isEmpty()
            } finally {
                scheduling.release()
                scheduler.stopScheduler()
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `notification concurrent with shutdown is ignored without rejected execution`(atInterval: Boolean) {
        val timer = ControlledTimer()
        val shuttingDown = BlockingCall()
        timer.beforeShutdown = shuttingDown::block
        val batches = mutableListOf<Set<VirtualFile>>()
        val scheduler = EventScheduler(100, atInterval, timer.executor, batches::add)

        withWorkers { workers ->
            try {
                val stopping = workers.submit { scheduler.stopScheduler() }
                shuttingDown.awaitEntered()
                val notification = workers.submit { scheduler.notify(mock()) }
                notification.get(5, TimeUnit.SECONDS)
                shuttingDown.release()
                stopping.get(5, TimeUnit.SECONDS)
                scheduler.stopScheduler()

                assertThat(timer.tasks).isEmpty()
                assertThat(timer.shutdownCalls.get()).isEqualTo(1)
                assertThat(batches).isEmpty()
            } finally {
                shuttingDown.release()
                scheduler.stopScheduler()
            }
        }
    }

    @Test
    fun `stop interrupts an in flight batch without waiting for it to complete`() {
        val executor = Executors.newSingleThreadScheduledExecutor()
        val sending = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val scheduler = EventScheduler(0, false, executor) {
            sending.countDown()
            try {
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue()
            } catch (_: InterruptedException) {
                interrupted.countDown()
            }
        }
        try {
            scheduler.notify(mock())
            assertThat(sending.await(5, TimeUnit.SECONDS)).isTrue()
            scheduler.stopScheduler()
            assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue()
        } finally {
            release.countDown()
            scheduler.stopScheduler()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    private fun withWorkers(action: (ExecutorService) -> Unit) {
        val workers = Executors.newFixedThreadPool(2)
        try {
            action(workers)
        } finally {
            workers.shutdownNow()
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }
    }

    private class BlockingCall {
        private val entered = CountDownLatch(1)
        private val released = CountDownLatch(1)

        fun block() {
            entered.countDown()
            assertThat(released.await(5, TimeUnit.SECONDS)).isTrue()
        }

        fun awaitEntered() {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
        }

        fun release() {
            released.countDown()
        }
    }

    private class ControlledTimer {
        val executor = mock<ScheduledExecutorService>()
        val shutdownCalls = AtomicInteger()
        var beforeSchedule: () -> Unit = {}
        var beforeShutdown: () -> Unit = {}
        private val shutdown = AtomicBoolean()
        private var now = 0L
        private val scheduledTasks = mutableListOf<Task>()
        val tasks: List<Task>
            get() = synchronized(this) { scheduledTasks.toList() }

        init {
            whenever(executor.schedule(any<Runnable>(), any<Long>(), eq(TimeUnit.MILLISECONDS))).thenAnswer { invocation ->
                if (shutdown.get()) throw RejectedExecutionException()
                beforeSchedule()
                val task = synchronized(this) {
                    Task(invocation.getArgument(0), now + invocation.getArgument<Long>(1)).also { scheduledTasks.add(it) }
                }
                task.future
            }
            whenever(executor.shutdownNow()).thenAnswer {
                shutdownCalls.incrementAndGet()
                shutdown.set(true)
                beforeShutdown()
                emptyList<Runnable>()
            }
        }

        fun elapseBy(milliseconds: Long) {
            synchronized(this) { now += milliseconds }
        }

        fun runNext(): Boolean {
            val task = synchronized(this) {
                scheduledTasks.firstOrNull { !it.cancelled && !it.started && it.deadline <= now }?.also { it.started = true }
            } ?: return false
            task.callback.run()
            return true
        }
    }

    private class Task(val callback: Runnable, val deadline: Long) {
        val future = mock<ScheduledFuture<Any>>()
        @Volatile var cancelled = false
        @Volatile var started = false
        var cancelledWithInterrupt = false

        init {
            whenever(future.cancel(any())).thenAnswer {
                cancelled = true
                cancelledWithInterrupt = it.getArgument(0)
                true
            }
        }
    }
}
