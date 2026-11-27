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
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import org.mockito.MockedStatic
import org.mockito.Mockito

internal class FixtureProjectLocator(private val delegate: ProjectLocator) : ProjectLocator() {
    override fun guessProjectForFile(file: VirtualFile): Project? = delegate.guessProjectForFile(file)

    override fun getProjectsForFile(file: VirtualFile): Collection<Project?> {
        val application = ApplicationManager.getApplication()
        // An EDT write lock cannot be held while the worker acquires the index's read lock.
        if (application.isWriteAccessAllowed) return delegate.getProjectsForFile(file)
        val lookup = application.executeOnPooledThread(Callable { delegate.getProjectsForFile(file) })
        return try {
            // Preserve the SDK assertion's synchronous result without dispatching unrelated editor callbacks.
            lookup.get(10, TimeUnit.SECONDS)
        } finally {
            if (!lookup.isDone) lookup.cancel(true)
        }
    }

    companion object {
        fun install(): MockedStatic<ProjectLocator> {
            val original = ProjectLocator.getInstance()
            val fixtureLocator = FixtureProjectLocator(original)
            return Mockito.mockStatic(ProjectLocator::class.java, Mockito.withSettings().stubOnly().defaultAnswer { invocation ->
                if (invocation.method.name != "getInstance") {
                    invocation.callRealMethod()
                } else if (isFixtureOwnershipLookup()) {
                    fixtureLocator
                } else {
                    original
                }
            })
        }

        private fun isFixtureOwnershipLookup(): Boolean {
            val caller = Thread.currentThread().stackTrace
                .dropWhile { it.className != ProjectLocator::class.java.name || it.methodName != "getInstance" }
                .getOrNull(1) ?: return false
            return (caller.className == "com.intellij.psi.impl.PsiDocumentManagerImpl" &&
                caller.methodName == "assertFileIsFromCorrectProject") ||
                (caller.className == "com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl" &&
                    caller.methodName.startsWith("lambda\$addFileToProject\$"))
        }
    }
}
