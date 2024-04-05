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
package org.sonarlint.intellij.resharper;

import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.workspace.compiler.AppleClangCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.ClangClCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.GCCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind;
import com.jetbrains.rider.cpp.fileType.CppLanguage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sonarlint.intellij.common.analysis.ForcedLanguage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AnalyzerConfigurationTest {

  @Test
  void get_sonar_language() {
    assertEquals(ForcedLanguage.CPP, AnalyzerConfiguration.getSonarLanguage(CppLanguage.INSTANCE));
  }

  @Test
  void map_to_cfamily_compiler() {
    assertEquals("clang", AnalyzerConfiguration.mapToCFamilyCompiler(ClangCompilerKind.INSTANCE));
    assertEquals("clang", AnalyzerConfiguration.mapToCFamilyCompiler(GCCCompilerKind.INSTANCE));
    assertEquals("clang-cl", AnalyzerConfiguration.mapToCFamilyCompiler(ClangClCompilerKind.INSTANCE));
    assertEquals("msvc-cl", AnalyzerConfiguration.mapToCFamilyCompiler(MSVCCompilerKind.INSTANCE));
    assertEquals("clang", AnalyzerConfiguration.mapToCFamilyCompiler(AppleClangCompilerKind.INSTANCE));
  }

  @Test
  void configuration() {
    var file = mock(VirtualFile.class);
    var configuration = new AnalyzerConfiguration.Configuration(
      file,
      "compilerExecutable",
      "compilerWorkingDir",
      List.of("s1", "s2"),
      "compilerKind",
      ForcedLanguage.CPP,
      Map.of("isHeaderFile", "true"));

    assertEquals(file, configuration.virtualFile());
    assertEquals("compilerExecutable", configuration.compilerExecutable());
    assertEquals("compilerWorkingDir", configuration.compilerWorkingDir());
    assertEquals(List.of("s1", "s2"), configuration.compilerSwitches());
    assertEquals("compilerKind", configuration.compilerKind());
    assertEquals(ForcedLanguage.CPP, configuration.sonarLanguage());
    assertEquals("true", configuration.properties().get("isHeaderFile"));
  }

  @Test
  void configuration_result() {
    var configuration = new AnalyzerConfiguration.Configuration(
      null,
      null,
      null,
      null,
      null,
      null,
      Map.of("isHeaderFile", "false"));
    var result = AnalyzerConfiguration.ConfigurationResult.of(configuration);
    assertTrue(result.hasConfiguration());
    assertEquals(configuration, result.getConfiguration());
    assertThrows(UnsupportedOperationException.class, result::getSkipReason);
  }

  @Test
  void configuration_result_skipped() {
    var result = AnalyzerConfiguration.ConfigurationResult.skip("reason");
    assertFalse(result.hasConfiguration());
    assertEquals("reason", result.getSkipReason());
    assertThrows(UnsupportedOperationException.class, result::getConfiguration);
  }
}
