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
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.sonarsource.sonarlint.core.client.api.common.Language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AnalyzerConfigurationTest {

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
  }

  @Test
  void configuration() {
    VirtualFile file = mock(VirtualFile.class);
    AnalyzerConfiguration.Configuration configuration = new AnalyzerConfiguration.Configuration(
      file,
      "compilerExecutable",
      "compilerWorkingDir",
      Arrays.asList("s1", "s2"),
      "compilerKind",
      Language.CPP,
      Collections.singletonMap("isHeaderFile", "true"));

    assertEquals(file, configuration.virtualFile);
    assertEquals("compilerExecutable", configuration.compilerExecutable);
    assertEquals("compilerWorkingDir", configuration.compilerWorkingDir);
    assertEquals(Arrays.asList("s1", "s2"), configuration.compilerSwitches);
    assertEquals("compilerKind", configuration.compilerKind);
    assertEquals(Language.CPP, configuration.sonarLanguage);
    assertEquals("true", configuration.properties.get("isHeaderFile"));
  }

  @Test
  void configuration_result() {
    AnalyzerConfiguration.Configuration configuration = new AnalyzerConfiguration.Configuration(
      null,
      null,
      null,
      null,
      null,
      null,
      Collections.singletonMap("isHeaderFile", "false"));
    AnalyzerConfiguration.ConfigurationResult result = AnalyzerConfiguration.ConfigurationResult.of(configuration);
    assertTrue(result.hasConfiguration());
    assertEquals(configuration, result.getConfiguration());
    assertThrows(UnsupportedOperationException.class, result::getSkipReason);
  }

  @Test
  void configuration_result_skipped() {
    AnalyzerConfiguration.ConfigurationResult result = AnalyzerConfiguration.ConfigurationResult.skip("reason");
    assertFalse(result.hasConfiguration());
    assertEquals("reason", result.getSkipReason());
    assertThrows(UnsupportedOperationException.class, result::getConfiguration);
  }
}
