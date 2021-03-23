/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2021 SonarSource
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
package org.sonarlint.intellij.issue.tracking

import java.util.*

class SingleIssueTracking<RAW : Trackable?, BASE : Trackable?>(
    private val raw: RAW, baseInput: Input<BASE>?
) {
    /**
     * Matched issues -> a raw issue is associated to a base issue
     */
    private val rawToBase = IdentityHashMap<RAW, BASE>()
    private val baseToRaw = IdentityHashMap<BASE, RAW>()
    private val bases: Collection<BASE>

    /**
     * Returns an Iterable to be traversed when matching issues. That means
     * that the traversal does not fail if method [.match]
     * is called.
     */
    val unmatchedRaw: RAW?
        get() {
            if (!rawToBase.containsKey(raw)) {
                return raw
            }
            return null
        }
    val matchedRaws: Map<RAW, BASE>
        get() = rawToBase

    fun baseFor(raw: RAW): BASE? {
        return rawToBase[raw]
    }

    /**
     * The base issues that are not matched by a raw issue and that need to be closed.
     */
    val unmatchedBases: Iterable<BASE>
        get() {
            val result: MutableList<BASE> = ArrayList()
            for (b in bases) {
                if (!baseToRaw.containsKey(b)) {
                    result.add(b)
                }
            }
            return result
        }

    fun containsUnmatchedBase(base: BASE): Boolean {
        return !baseToRaw.containsKey(base)
    }

    fun match(raw: RAW, base: BASE) {
        rawToBase[raw] = base
        baseToRaw[base] = raw
    }

    init {
        bases = baseInput!!.issues
    }
}