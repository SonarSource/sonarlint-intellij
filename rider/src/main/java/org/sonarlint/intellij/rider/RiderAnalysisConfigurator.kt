/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015-2025 SonarSource Sàrl
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
package org.sonarlint.intellij.rider

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rd.ide.model.RdExistingSolution
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.solutionDescription
import com.jetbrains.rider.projectView.solutionFile
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import org.sonarlint.intellij.common.analysis.AnalysisConfigurator
import org.sonarlint.intellij.common.analysis.AnalysisConfigurator.AnalysisConfiguration

open class RiderAnalysisConfigurator : AnalysisConfigurator {

    override fun configure(module: Module, filesToAnalyze: Collection<VirtualFile>): AnalysisConfiguration {
        val result = AnalysisConfiguration()

        val dotNetCoreRuntime = RiderDotNetActiveRuntimeHost.getInstance(module.project).dotNetCoreRuntime.value
        if (dotNetCoreRuntime != null) {
            val cliPath = getCliExePath(dotNetCoreRuntime)
            if (cliPath != null) {
                result.extraProperties["sonar.cs.internal.dotnetCliExeLocation"] = cliPath
            }
        }
        
        val monoRuntime = RiderDotNetActiveRuntimeHost.getInstance(module.project).monoRuntime
        if (monoRuntime != null) {
            val monoPath = getMonoExePath(monoRuntime)
            if (monoPath != null) {
                result.extraProperties["sonar.cs.internal.monoExeLocation"] = monoPath
            }
        }
        
        if (module.project.solutionDescription is RdExistingSolution) {
            val solutionFile: Any? = module.project.solutionFile
            val solutionPath = convertToString(solutionFile)
            if (solutionPath != null) {
                result.extraProperties["sonar.cs.internal.solutionPath"] = solutionPath
            }
        }

        val msBuildPathValue: Any? = module.project.solution.activeMsBuildPath.value
        val msBuildPathStr = getMsBuildPath(msBuildPathValue)
        if (msBuildPathStr != null) {
            val msBuildPath = Paths.get(msBuildPathStr)
            result.extraProperties["sonar.cs.internal.msBuildPath"] = if (msBuildPath.isRegularFile()) msBuildPath.parent.toString() else msBuildPath.toString()
        }
        
        return result
    }

    /**
     * Get CLI exe path from DotNetCoreRuntime
     * In older versions: getCliExePath() returns String
     * In Rider 2026.1+: the method signature or return type may have changed
     */
    internal fun getCliExePath(runtime: Any): String? {
        return safelyGetPath(runtime, "getCliExePath")
    }

    /**
     * Convert MSBuild path to String
     * In older versions: returns String directly
     * In Rider 2026.1+: returns RdPath object
     */
    internal fun getMsBuildPath(pathValue: Any?): String? {
        return convertToString(pathValue)
    }

    /**
     * Safely invoke a method on a runtime object and convert the result to a path string.
     * This handles API changes across Rider versions where methods may not exist or return different types.
     */
    internal fun safelyGetPath(runtime: Any, methodName: String): String? {
        return try {
            val method = runtime.javaClass.getMethod(methodName)
            val result = method.invoke(runtime)
            convertToString(result)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Get Mono exe path from MonoRuntime
     * In older versions: getMonoExe() returns File
     * In Rider 2026.1+: the method signature or return type may have changed
     */
    internal fun getMonoExePath(runtime: Any): String? {
        return safelyGetPath(runtime, "getMonoExe")
    }

    internal fun convertToString(pathValue: Any?): String? {
        return when (pathValue) {
            null -> null
            is String -> pathValue
            is File -> pathValue.absolutePath
            else -> try {
                // For RdPath and other types, toString() should return the path
                pathValue.toString()
            } catch (e: Exception) {
                null
            }
        }
    }
    
}
