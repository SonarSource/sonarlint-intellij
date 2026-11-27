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

import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.RunAll
import com.intellij.util.SlowOperations
import com.intellij.util.ui.EDT
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import org.mockito.MockedStatic
import org.mockito.Mockito

class SlowOperationGuard : AutoCloseable {
    private val reports = ConcurrentLinkedQueue<Throwable>()
    private val logger = Logger.getLogger("#${SlowOperations::class.java.name}")
    private val originalLevel = logger.level
    private val registryOverrides = Disposer.newDisposable("Light test slow operation registry overrides")
    private var fixtureLocatorOverride: MockedStatic<ProjectLocator>? = null
    private val handler = object : Handler() {
        override fun publish(record: LogRecord) {
            if (record.message?.startsWith("Slow operations are prohibited on EDT.") == true) {
                reports.add(record.thrown ?: AssertionError(record.message))
            }
        }

        override fun flush() = Unit
        override fun close() = Unit
    }

    // On IntelliJ 2024.2 unit-test mode short-circuits the registry. Mockito's override is local to the EDT.
    private val bypassOverride = run {
        EDT.assertIsEdt()
        Mockito.mockStatic(
            SlowOperations::class.java,
            Mockito.withSettings().stubOnly().defaultAnswer { invocation ->
                if (invocation.method.name == "assertSlowOperationsAreAllowed" && isTrackedSlowOperation()) {
                    null
                } else {
                    invocation.callRealMethod()
                }
            },
        )
    }

    init {
        try {
            bypassOverride.`when`<Boolean>(SlowOperations::isAlwaysAllowed).thenReturn(false)
            fixtureLocatorOverride = FixtureProjectLocator.install()
            Registry.get("ide.slow.operations.assertion").setValue(true, registryOverrides)
            Registry.get("ide.slow.operations.assertion.other").setValue(true, registryOverrides)
            // TestLoggerFactory sends error reports to JUL at INFO before throwing.
            if (!logger.isLoggable(Level.INFO)) {
                logger.level = Level.INFO
            }
            logger.addHandler(handler)
        } catch (failure: Throwable) {
            runCatching { close() }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    fun assertNoViolations() {
        val violations = reports.toList()
        if (violations.isNotEmpty()) {
            val failure = AssertionError("IntelliJ reported ${violations.size} slow operation(s) on the EDT")
            violations.forEach(failure::addSuppressed)
            throw failure
        }
    }

    override fun close() {
        RunAll.runAll(
            { fixtureLocatorOverride?.close() },
            { bypassOverride.close() },
            { Disposer.dispose(registryOverrides) },
            {
                logger.removeHandler(handler)
                logger.level = originalLevel
            },
        )
    }

    private fun isTrackedSlowOperation(): Boolean {
        val stack = Thread.currentThread().stackTrace
        fun contains(owner: String, method: String) = stack.any { it.className == owner && it.methodName == method }

        // Other IntelliJ 2024.2 fixture paths that do not use the project-ownership lookup.
        if (contains("com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexDataImpl", "ensureIsUpToDate")) {
            val opensEditor = contains("com.intellij.openapi.fileEditor.impl.TestEditorManagerImpl", "doOpenTextEditor")
            val daemonListener = contains("com.intellij.codeInsight.daemon.impl.DaemonListeners\$4", "editorCreated")
            val fixture = "com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl"
            val validatesFixtureFile = contains(fixture, "addFileToProject") &&
                contains("com.intellij.psi.impl.PsiManagerImpl", "findFile")
            val cacheCleanup = contains("com.intellij.testFramework.common.TestApplicationKt", "cleanupApplicationCaches") &&
                contains("com.intellij.util.indexing.events.ChangedFilesCollector", "processFilesInReadAction")
            return (opensEditor && daemonListener) || validatesFixtureFile || cacheCleanup
        }

        // SLI-3097: the affected MCP adapter refreshes the configuration path on the EDT.
        return contains("com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl", "update") &&
            contains("org.sonarlint.intellij.ai.IntellijMcpUiAdapter", "openConfiguration")
    }
}
