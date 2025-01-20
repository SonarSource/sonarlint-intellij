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
package org.sonarlint.intellij.callable

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.sonarlint.intellij.analysis.AnalysisCallback
import org.sonarlint.intellij.analysis.AnalysisResult

class CheckInCallable : AnalysisCallback {

    private var resultsPerAnalysis = mutableMapOf<UUID, AnalysisResult>()
    private val errored = AtomicBoolean(false)

    override fun onSuccess(analysisResult: AnalysisResult) {
        analysisResult.analysisId?.let { resultsPerAnalysis[it] = analysisResult }
    }

    override fun onError(e: Throwable) {
        errored.set(true)
    }

    fun analysisSucceeded(): Boolean {
        return !errored.get()
    }

    fun getResults(): List<AnalysisResult> {
        return resultsPerAnalysis.values.toList()
    }

}
