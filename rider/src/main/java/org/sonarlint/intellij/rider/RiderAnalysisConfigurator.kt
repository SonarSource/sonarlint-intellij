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
package org.sonarlint.intellij.rider

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rd.ide.model.RdExistingSolution
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.projectView.solutionDescription
import com.jetbrains.rider.projectView.solutionFile
import com.jetbrains.rider.runtime.RiderDotNetActiveRuntimeHost
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import org.sonarlint.intellij.common.analysis.AnalysisConfigurator
import org.sonarlint.intellij.common.analysis.AnalysisConfigurator.AnalysisConfiguration

class RiderAnalysisConfigurator : AnalysisConfigurator {

    override fun configure(module: Module, filesToAnalyze: Collection<VirtualFile>): AnalysisConfiguration {
        val result = AnalysisConfiguration()
        val dotNetCoreRuntime = RiderDotNetActiveRuntimeHost.getInstance(module.project).dotNetCoreRuntime.value
        if (dotNetCoreRuntime != null) {
            result.extraProperties["sonar.cs.internal.dotnetCliExeLocation"] = getCliExePath(dotNetCoreRuntime)
        }
        val monoRuntime = RiderDotNetActiveRuntimeHost.getInstance(module.project).monoRuntime
        if (monoRuntime != null) {
            result.extraProperties["sonar.cs.internal.monoExeLocation"] = getMonoExePath(monoRuntime)
        }
        if (module.project.solutionDescription is RdExistingSolution) {
            result.extraProperties["sonar.cs.internal.solutionPath"] = module.project.solutionFile.absolutePath
        }
        val msBuildPathStr = getMsBuildPathString(module)
        if (msBuildPathStr != null) {
            val msBuildPath = Paths.get(msBuildPathStr)
            result.extraProperties["sonar.cs.internal.msBuildPath"] = if (msBuildPath.isRegularFile()) msBuildPath.parent.toString() else msBuildPath.toString()
        }
        return result
    }

    internal fun getCliExePath(dotNetCoreRuntime: Any): String {
        return when (val cliExePath = dotNetCoreRuntime.javaClass.getMethod("getCliExePath").invoke(dotNetCoreRuntime)) {
            is Path -> cliExePath.normalize().pathString
            is String -> cliExePath
            else -> cliExePath.toString()
        }
    }

    internal fun getMonoExePath(monoRuntime: Any): String {
        return when (val monoExe = monoRuntime.javaClass.getMethod("getMonoExe").invoke(monoRuntime)) {
            is Path -> monoExe.toAbsolutePath().normalize().pathString
            else -> {
                val absolutePathMethod = monoExe.javaClass.getMethod("getAbsolutePath")
                absolutePathMethod.invoke(monoExe) as String
            }
        }
    }

    internal fun getMsBuildPathString(module: Module): String? {
        return try {
            // Use reflection to access the 'value' property to avoid ClassNotFoundException
            val activeMsBuildPath = module.project.solution.activeMsBuildPath
            val valueProperty = activeMsBuildPath.javaClass.getMethod("getValue")
            val msBuildPathValue = valueProperty.invoke(activeMsBuildPath) ?: return null
            
            when (msBuildPathValue) {
                is String -> msBuildPathValue
                else -> {
                    // For RdPath or other wrapper types, try to get the inner value
                    try {
                        val innerValueMethod = msBuildPathValue.javaClass.getMethod("getValue")
                        when (val innerValue = innerValueMethod.invoke(msBuildPathValue)) {
                            is String -> innerValue
                            is Path -> innerValue.normalize().pathString
                            else -> innerValue?.toString()
                        }
                    } catch (_: NoSuchMethodException) {
                        // Fallback to toString if no getValue method
                        msBuildPathValue.toString()
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

}
