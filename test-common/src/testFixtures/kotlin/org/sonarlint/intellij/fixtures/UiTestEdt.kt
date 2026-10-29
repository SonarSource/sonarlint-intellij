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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil

/** Flushes the IDE event queue; safe from Awaitility poll threads as well as the EDT. */
fun pumpEdt() {
    val app = ApplicationManager.getApplication()
    if (app.isDispatchThread) {
        UIUtil.dispatchAllInvocationEvents()
    } else {
        runInEdtAndWait { UIUtil.dispatchAllInvocationEvents() }
    }
}

/** Runs [block] on the EDT after pumping the queue; safe from Awaitility poll threads. */
fun <T> readOnEdt(block: () -> T): T {
    val app = ApplicationManager.getApplication()
    if (app.isDispatchThread) {
        UIUtil.dispatchAllInvocationEvents()
        return block()
    }
    var result: T? = null
    runInEdtAndWait {
        UIUtil.dispatchAllInvocationEvents()
        result = block()
    }
    @Suppress("UNCHECKED_CAST")
    return result as T
}

/** Pumps the EDT until [condition] is true or [timeoutMs] elapses. */
fun pumpEdtUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        pumpEdt()
        if (condition()) {
            return
        }
    }
    error("Condition was not met within ${timeoutMs}ms on EDT")
}
