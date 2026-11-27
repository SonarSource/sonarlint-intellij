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

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.util.IdentityHashMap

class DisposableLeakTracker(private vararg val roots: Disposable) {
    private val baseline = registrations()

    fun assertNoLeaks() {
        val remaining = registrations()
        val survivors = remaining.keys.filter { baseline[it] !== remaining[it] }
        if (survivors.isNotEmpty()) {
            val paths = survivors.map { child ->
                generateSequence(child) { remaining[it] }.toList().asReversed().joinToString(" -> ")
            }
            val failure = AssertionError(paths.sorted().joinToString("\n  ", "Disposable registrations survived teardown:\n  "))
            survivors.mapNotNull(Disposer::getRegistrationTrace).forEach(failure::addSuppressed)
            throw failure
        }
    }

    private fun registrations(): Map<Disposable, Disposable> {
        val parents = IdentityHashMap<Disposable, Disposable>()
        fun visit(root: Disposable) {
            // On the pinned SDK the predicate sees direct children under the tree lock. Returning false keeps them registered.
            Disposer.disposeChildren(root) { child ->
                parents[child] = root
                visit(child)
                false
            }
        }
        roots.forEach(::visit)
        return parents
    }
}
