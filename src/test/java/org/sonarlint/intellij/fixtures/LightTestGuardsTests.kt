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
package org.sonarlint.intellij.fixtures

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.SlowOperations
import java.util.concurrent.CountDownLatch
import java.util.logging.Logger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

// Exercise the guards independently so deliberately failing a guard is part of the test.
class LightTestGuardsTests : BasePlatformTestCase() {

    @BeforeEach
    fun setUpFixture() = runInEdtAndWait {
        name = "lightTestGuards"
        super.setUp()
    }

    @AfterEach
    fun tearDownFixture() = runInEdtAndWait {
        super.tearDown()
    }

    @Test
    fun `reports a swallowed EDT violation and restores slow operation settings`() {
        val logger = Logger.getLogger("#com.intellij.util.SlowOperations")
        val originalLevel = logger.level
        val originalHandlers = logger.handlers
        val before = readOnEdt {
            listOf(
                Registry.`is`("ide.slow.operations.assertion"),
                Registry.`is`("ide.slow.operations.assertion.other"),
                SlowOperations.isAlwaysAllowed(),
            )
        }
        val file = readOnEdt { myFixture.addFileToProject("guard.txt", "hello").virtualFile }
        val guard = readOnEdt { SlowOperationGuard() }
        try {
            SlowOperations.assertSlowOperationsAreAllowed()
            readOnEdt { FileEditorManager.getInstance(project).openFile(file, false) }
            guard.assertNoViolations()
            runInEdtAndWait {
                val immediate = assertThrows<AssertionError> { SlowOperations.assertSlowOperationsAreAllowed() }
                assertThat(immediate).hasMessageContaining("Slow operations are prohibited on EDT")
            }
        } finally {
            runInEdtAndWait { guard.close() }
        }
        assertThat(assertThrows<AssertionError> { guard.assertNoViolations() }.stackTraceToString())
            .contains("Slow operations are prohibited on EDT", "SlowOperations.assertSlowOperationsAreAllowed")
        assertThat(logger.level).isEqualTo(originalLevel)
        assertThat(logger.handlers).containsExactly(*originalHandlers)
        runInEdtAndWait {
            assertThat(
                listOf(
                    Registry.`is`("ide.slow.operations.assertion"),
                    Registry.`is`("ide.slow.operations.assertion.other"),
                    SlowOperations.isAlwaysAllowed(),
                )
            ).isEqualTo(before)
        }
    }

    @Test
    fun `reports nested project registrations and accepts clean disposal`() = runInEdtAndWait {
        val parent = Disposer.newDisposable(project, "existing-project-child")
        try {
            val tracker = DisposableLeakTracker(testRootDisposable, project)
            val nested = Disposer.newDisposable(parent, "leaked-nested-child")
            val cleaned = Disposer.newDisposable(testRootDisposable, "cleaned-test-child")
            Disposer.dispose(cleaned)

            assertThat(assertThrows<AssertionError> { tracker.assertNoLeaks() }.stackTraceToString())
                .contains("existing-project-child", "leaked-nested-child")
                .doesNotContain("cleaned-test-child")

            Disposer.dispose(nested)
            tracker.assertNoLeaks()
        } finally {
            Disposer.dispose(parent)
        }
    }

    @Test
    fun `reports a second daemon with the same worker name and accepts exited workers`() {
        val stop = CountDownLatch(1)
        val baseline = guardThread(stop)
        val tracker = PluginThreadLeakTracker()
        val duplicate = guardThread(stop)
        val exited = guardThread(CountDownLatch(0))
        try {
            exited.join(5000)
            val failure = assertThrows<AssertionError> { tracker.assertNoLeaks() }
            assertThat(failure.message)
                .contains("id=${duplicate.threadId()}")
                .doesNotContain("id=${baseline.threadId()}", "id=${exited.threadId()}")
        } finally {
            stop.countDown()
            baseline.join(5000)
            duplicate.join(5000)
            exited.join(5000)
        }
        tracker.assertNoLeaks()
    }

    private fun guardThread(stop: CountDownLatch) =
        Thread({ stop.await() }, "sonarlint-auto-trigger-guard-test").apply {
            isDaemon = true
            start()
        }
}
