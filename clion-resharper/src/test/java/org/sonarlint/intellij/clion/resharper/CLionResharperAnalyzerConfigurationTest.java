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
package org.sonarlint.intellij.clion.resharper;

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

class CLionResharperAnalyzerConfigurationTest {

  @Test
  void get_sonar_language() {
    assertEquals(ForcedLanguage.CPP, CLionResharperAnalyzerConfiguration.getSonarLanguage(CppLanguage.INSTANCE));
  }

  @Test
  void map_to_cfamily_compiler() {
    assertEquals("clang", CLionResharperAnalyzerConfiguration.mapToCFamilyCompiler(ClangCompilerKind.INSTANCE));
    assertEquals("clang", CLionResharperAnalyzerConfiguration.mapToCFamilyCompiler(GCCCompilerKind.INSTANCE));
    assertEquals("clang-cl", CLionResharperAnalyzerConfiguration.mapToCFamilyCompiler(ClangClCompilerKind.INSTANCE));
    assertEquals("msvc-cl", CLionResharperAnalyzerConfiguration.mapToCFamilyCompiler(MSVCCompilerKind.INSTANCE));
    assertEquals("clang", CLionResharperAnalyzerConfiguration.mapToCFamilyCompiler(AppleClangCompilerKind.INSTANCE));
  }

  @Test
  void configuration() {
    var file = mock(VirtualFile.class);
    var configuration = new CLionResharperAnalyzerConfiguration.Configuration(
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
    var configuration = new CLionResharperAnalyzerConfiguration.Configuration(
      null,
      null,
      null,
      null,
      null,
      null,
      Map.of("isHeaderFile", "false"));
    var result = CLionResharperAnalyzerConfiguration.ConfigurationResult.of(configuration);
    assertTrue(result.hasConfiguration());
    assertEquals(configuration, result.getConfiguration());
    assertThrows(UnsupportedOperationException.class, result::getSkipReason);
  }

  @Test
  void configuration_result_skipped() {
    var result = CLionResharperAnalyzerConfiguration.ConfigurationResult.skip("reason");
    assertFalse(result.hasConfiguration());
    assertEquals("reason", result.getSkipReason());
    assertThrows(UnsupportedOperationException.class, result::getConfiguration);
  }
}
