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
package org.sonarlint.intellij.clion.common;

import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyzerConfigurationTest {
  @Test
  void testPreprocessorDefines() {
    var compilerSettings = mock(OCCompilerSettings.class);
    when(compilerSettings.getPreprocessorDefines()).thenReturn(List.of(
      "#define a b", "#define c d ", "   #define e f     ", " #define g h", " #define i j"));

    var preprocessorDefines = AnalyzerConfiguration.getPreprocessorDefines(compilerSettings);
    assertEquals("#define a b\n#define c d\n#define e f\n#define g h\n#define i j\n", preprocessorDefines);
  }
}
