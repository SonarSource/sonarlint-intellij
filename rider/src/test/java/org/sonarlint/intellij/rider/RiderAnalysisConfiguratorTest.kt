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
import java.nio.file.Path
import java.nio.file.Paths
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RiderAnalysisConfiguratorTest {

    private val configurator = RiderAnalysisConfigurator()

    @Test
    fun `getCliExePath should handle Path object from Rider 2026_1+`() {
        val mockRuntime = MockDotNetCoreRuntimeNewApi(Paths.get("/usr/bin/dotnet"))
        val result = configurator.getCliExePath(mockRuntime)
        
        assertThat(result).isEqualTo("/usr/bin/dotnet")
    }

    @Test
    fun `getCliExePath should handle String from Rider before 2026_1`() {
        val mockRuntime = MockDotNetCoreRuntimeOldApi("/usr/bin/dotnet")
        val result = configurator.getCliExePath(mockRuntime)

        assertThat(result).isEqualTo("/usr/bin/dotnet")
    }

    @Test
    fun `getMonoExePath should handle Path object from Rider 2026_1+`() {
        val mockRuntime = MockMonoRuntimeNewApi(Paths.get("/usr/bin/mono"))
        val result = configurator.getMonoExePath(mockRuntime)
        
        assertThat(result).isEqualTo("/usr/bin/mono")
    }

    @Test
    fun `getMonoExePath should handle File object from Rider before 2026_1`() {
        val mockRuntime = MockMonoRuntimeOldApi(File("/usr/bin/mono"))
        val result = configurator.getMonoExePath(mockRuntime)
        
        assertThat(result).isEqualTo("/usr/bin/mono")
    }

    @Test
    fun `getCliExePath should normalize path with dots`() {
        val mockRuntime = MockDotNetCoreRuntimeNewApi(Paths.get("/usr/local/../bin/dotnet"))
        val result = configurator.getCliExePath(mockRuntime)
        
        assertThat(result).isEqualTo("/usr/bin/dotnet")
    }

    @Test
    fun `getMonoExePath should normalize path with dots`() {
        val mockRuntime = MockMonoRuntimeNewApi(Paths.get("/usr/local/../bin/mono"))
        val result = configurator.getMonoExePath(mockRuntime)
        
        assertThat(result).isEqualTo("/usr/bin/mono")
    }

    @Test
    fun `getMonoExePath should convert relative path to absolute`() {
        val relativePath = Paths.get("mono")
        val mockRuntime = MockMonoRuntimeNewApi(relativePath)
        val result = configurator.getMonoExePath(mockRuntime)
        
        // Result should be absolute (not just "mono")
        assertThat(Paths.get(result).isAbsolute).isTrue
    }

    class MockDotNetCoreRuntimeNewApi(private val path: Path) {
        fun getCliExePath(): Path = path
    }

    class MockDotNetCoreRuntimeOldApi(private val path: String) {
        fun getCliExePath(): String = path
    }

    class MockMonoRuntimeNewApi(private val path: Path) {
        fun getMonoExe(): Path = path
    }

    class MockMonoRuntimeOldApi(private val file: File) {
        fun getMonoExe(): File = file
    }

}
