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

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RiderAnalysisConfiguratorTest {

    private val configurator = RiderAnalysisConfigurator()

    @Test
    fun `convertToString should handle null`() {
        val result = configurator.convertToString(null)
        
        assertThat(result).isNull()
    }

    @Test
    fun `convertToString should handle String directly`() {
        val result = configurator.convertToString("/path/to/file")
        
        assertThat(result).isEqualTo("/path/to/file")
    }

    @Test
    fun `convertToString should handle File object`() {
        val file = File("/path/to/file")
        val result = configurator.convertToString(file)
        
        assertThat(result).isEqualTo(file.absolutePath)
    }

    @Test
    fun `convertToString should use toString for unknown types`() {
        val customPath = CustomPathObject("/custom/path")
        val result = configurator.convertToString(customPath)
        
        assertThat(result).isEqualTo("/custom/path")
    }

    @Test
    fun `convertToString should handle object with broken toString`() {
        val brokenObject = object {
            override fun toString(): String {
                throw RuntimeException("toString failed")
            }
        }
        
        val result = configurator.convertToString(brokenObject)
        
        assertThat(result).isNull()
    }

    @Test
    fun `safelyGetPath should handle method invocation with String result`() {
        val runtime = RuntimeWithStringMethod()
        val result = configurator.safelyGetPath(runtime, "getCliExePath")
        
        assertThat(result).isEqualTo("/usr/bin/dotnet")
    }

    @Test
    fun `safelyGetPath should handle method invocation with File result`() {
        val runtime = RuntimeWithFileMethod()
        val result = configurator.safelyGetPath(runtime, "getMonoExe")
        
        assertThat(result).isEqualTo(File("/usr/bin/mono").absolutePath)
    }

    @Test
    fun `safelyGetPath should handle method invocation with custom path object`() {
        val runtime = RuntimeWithCustomPathMethod()
        val result = configurator.safelyGetPath(runtime, "getPath")
        
        assertThat(result).isEqualTo("/custom/path/location")
    }

    @Test
    fun `safelyGetPath should handle missing method gracefully`() {
        val runtime = RuntimeWithNoMethod()
        val result = configurator.safelyGetPath(runtime, "nonExistentMethod")
        
        assertThat(result).isNull()
    }

    @Test
    fun `safelyGetPath should handle method returning null`() {
        val runtime = RuntimeWithNullMethod()
        val result = configurator.safelyGetPath(runtime, "getPath")
        
        assertThat(result).isNull()
    }

    @Test
    fun `safelyGetPath should handle method throwing exception`() {
        val runtime = RuntimeWithThrowingMethod()
        val result = configurator.safelyGetPath(runtime, "getPath")
        
        assertThat(result).isNull()
    }

    @Test
    fun `getCliExePath should delegate to safelyGetPath`() {
        val runtime = RuntimeWithStringMethod()
        val result = configurator.getCliExePath(runtime)
        
        assertThat(result).isEqualTo("/usr/bin/dotnet")
    }

    @Test
    fun `getMonoExePath should delegate to safelyGetPath`() {
        val runtime = RuntimeWithFileMethod()
        val result = configurator.getMonoExePath(runtime)
        
        assertThat(result).isEqualTo(File("/usr/bin/mono").absolutePath)
    }

    @Test
    fun `getMsBuildPath should handle String`() {
        val result = configurator.getMsBuildPath("/path/to/msbuild")
        
        assertThat(result).isEqualTo("/path/to/msbuild")
    }

    @Test
    fun `getMsBuildPath should handle File`() {
        val result = configurator.getMsBuildPath(File("/path/to/msbuild"))
        
        assertThat(result).isEqualTo(File("/path/to/msbuild").absolutePath)
    }

    @Test
    fun `getMsBuildPath should handle custom path objects`() {
        val result = configurator.getMsBuildPath(CustomPathObject("/path/to/msbuild"))
        
        assertThat(result).isEqualTo("/path/to/msbuild")
    }

    @Test
    fun `getMsBuildPath should handle null`() {
        val result = configurator.getMsBuildPath(null)
        
        assertThat(result).isNull()
    }

    // Test helper classes simulating different Rider runtime versions

    class RuntimeWithStringMethod {
        fun getCliExePath(): String = "/usr/bin/dotnet"
    }

    class RuntimeWithFileMethod {
        fun getMonoExe(): File = File("/usr/bin/mono")
    }

    class RuntimeWithCustomPathMethod {
        fun getPath(): CustomPathObject = CustomPathObject("/custom/path/location")
    }

    class RuntimeWithNoMethod {
        // No methods at all
    }

    class RuntimeWithNullMethod {
        fun getPath(): String? = null
    }

    class RuntimeWithThrowingMethod {
        fun getPath(): String {
            throw RuntimeException("Method failed")
        }
    }

    data class CustomPathObject(private val path: String) {
        override fun toString(): String = path
    }

}
