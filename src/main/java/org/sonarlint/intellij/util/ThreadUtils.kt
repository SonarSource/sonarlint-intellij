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
package org.sonarlint.intellij.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import java.time.Duration
import java.util.concurrent.Callable
import org.sonarlint.intellij.util.FutureUtils.waitForTask
import org.sonarlint.intellij.util.FutureUtils.waitForTaskWithoutCatching


fun runOnPooledThread(runnable: Runnable) {
    ApplicationManager.getApplication().executeOnPooledThread {
        runnable.run()
    }
}

fun runOnPooledThread(project: Project, runnable: Runnable) {
    ApplicationManager.getApplication().executeOnPooledThread {
        if (!project.isDisposed) {
            runnable.run()
        }
    }
}

fun <T> computeOnPooledThread(project: Project, taskName: String, callable: Callable<T>): T? {
    return waitForTask(ApplicationManager.getApplication().executeOnPooledThread<T> {
        if (!project.isDisposed) {
            callable.call()
        } else {
            null
        }
    }, taskName, Duration.ofSeconds(30))
}

fun <T> computeOnPooledThreadWithoutCatching(project: Project, taskName: String, callable: Callable<T>): T? {
    return waitForTaskWithoutCatching(ApplicationManager.getApplication().executeOnPooledThread<T> {
        if (!project.isDisposed) {
            callable.call()
        } else {
            null
        }
    }, taskName, Duration.ofSeconds(30))
}

fun <T> computeOnPooledThread(taskName: String, callable: Callable<T>): T? {
    return waitForTask(ApplicationManager.getApplication().executeOnPooledThread<T> {
        callable.call()
    }, taskName, Duration.ofSeconds(30))
}

