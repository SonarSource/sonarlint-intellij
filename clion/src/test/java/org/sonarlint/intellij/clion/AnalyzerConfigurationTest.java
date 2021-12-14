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
package org.sonarlint.intellij.clion;

import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.CUDALanguageKind;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrSwitchBuilder;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerSpecificSwitchBuilder;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompiler;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.TempFilesPool;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.commons.Language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AnalyzerConfigurationTest {

  /**
   * 2021.3 differentiates AppleClang from Clang
   */
  private static final OCCompilerKind APPLE_CLANG_COMPILER = new OCCompilerKind() {
    @Override
    public @NotNull String getDisplayName() {
      return "AppleClang";
    }

    @Override
    public @Nullable OCLanguageKind resolveLanguage(@NotNull List<String> list) {
      return null;
    }

    @Override
    public @Nullable CompilerSpecificSwitchBuilder getSwitchBuilder(@NotNull CidrSwitchBuilder cidrSwitchBuilder) {
      return null;
    }

    @Override
    public @NotNull OCCompiler getCompilerInstance(@NotNull File file, @NotNull File file1, @NotNull CidrToolEnvironment cidrToolEnvironment, @NotNull TempFilesPool tempFilesPool) {
      return null;
    }
  };

  @Test
  void get_sonar_language() {
    assertEquals(Language.C, AnalyzerConfiguration.getSonarLanguage(CLanguageKind.C));
    assertEquals(Language.CPP, AnalyzerConfiguration.getSonarLanguage(CLanguageKind.CPP));
    assertEquals(Language.OBJC, AnalyzerConfiguration.getSonarLanguage(CLanguageKind.OBJ_C));
    assertNull(AnalyzerConfiguration.getSonarLanguage(CLanguageKind.OBJ_CPP));
    assertNull(AnalyzerConfiguration.getSonarLanguage(CUDALanguageKind.CUDA));
  }

  @Test
  void map_to_cfamily_compiler() {
    assertEquals("clang", AnalyzerConfiguration.mapToCFamilyCompiler(OCCompilerKind.CLANG));
    assertEquals("clang", AnalyzerConfiguration.mapToCFamilyCompiler(OCCompilerKind.GCC));
    assertNull(AnalyzerConfiguration.mapToCFamilyCompiler(OCCompilerKind.CLANG_CL));
    assertEquals("msvc-cl", AnalyzerConfiguration.mapToCFamilyCompiler(OCCompilerKind.MSVC));
    assertEquals("clang", AnalyzerConfiguration.mapToCFamilyCompiler(APPLE_CLANG_COMPILER));
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
      Language.CPP,
      Map.of("isHeaderFile", "true"));

    assertEquals(file, configuration.virtualFile);
    assertEquals("compilerExecutable", configuration.compilerExecutable);
    assertEquals("compilerWorkingDir", configuration.compilerWorkingDir);
    assertEquals(List.of("s1", "s2"), configuration.compilerSwitches);
    assertEquals("compilerKind", configuration.compilerKind);
    assertEquals(Language.CPP, configuration.sonarLanguage);
    assertEquals("true", configuration.properties.get("isHeaderFile"));
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
