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
package org.sonarlint.intellij.clion;

import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchPath;
import java.util.HashMap;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyzerConfigurationTests {

  @Test
  void testPreprocessorDefines() {
    var compilerSettings = mock(OCCompilerSettings.class);
    when(compilerSettings.getPreprocessorDefines()).thenReturn(List.of(
      "#define a b", "#define c d ", "   #define e f     ", " #define g h", " #define i j"));

    var preprocessorDefines = AnalyzerConfiguration.getPreprocessorDefines(compilerSettings);
    assertEquals("#define a b\n#define c d\n#define e f\n#define g h\n#define i j\n", preprocessorDefines);
  }

  @Test
  void testCollectDefinesAndIncludes_withAllHeadersFilter() {
    var compilerSettings = mock(OCCompilerSettings.class);
    when(compilerSettings.getPreprocessorDefines()).thenReturn(List.of("#define TEST 1"));

    var header1 = mock(HeadersSearchPath.class);
    when(header1.getPath()).thenReturn("/usr/include");
    var header2 = mock(HeadersSearchPath.class);
    when(header2.getPath()).thenReturn("/usr/local/include");
    var header3 = mock(HeadersSearchPath.class);
    when(header3.getPath()).thenReturn("/opt/include");

    when(compilerSettings.getHeadersSearchPaths()).thenReturn(List.of(header1, header2, header3));

    var properties = new HashMap<String, String>();
    Predicate<HeadersSearchPath> allHeadersFilter = h -> true;

    AnalyzerConfiguration.collectDefinesAndIncludes(compilerSettings, properties, allHeadersFilter);

    assertEquals("#define TEST 1\n", properties.get("preprocessorDefines"));
    assertEquals("/usr/include\n/usr/local/include\n/opt/include", properties.get("builtinHeaders"));
  }

  @Test
  void testCollectDefinesAndIncludes_withBuiltinHeadersFilter() {
    var compilerSettings = mock(OCCompilerSettings.class);
    when(compilerSettings.getPreprocessorDefines()).thenReturn(List.of("#define DEBUG 1", "#define VERSION 2"));

    var builtinHeader = mock(HeadersSearchPath.class);
    when(builtinHeader.getPath()).thenReturn("/builtin/include");
    when(builtinHeader.isBuiltInHeaders()).thenReturn(true);

    var userHeader = mock(HeadersSearchPath.class);
    when(userHeader.getPath()).thenReturn("/user/include");
    when(userHeader.isBuiltInHeaders()).thenReturn(false);

    var systemHeader = mock(HeadersSearchPath.class);
    when(systemHeader.getPath()).thenReturn("/system/include");
    when(systemHeader.isBuiltInHeaders()).thenReturn(false);

    when(compilerSettings.getHeadersSearchPaths()).thenReturn(List.of(builtinHeader, userHeader, systemHeader));

    var properties = new HashMap<String, String>();
    Predicate<HeadersSearchPath> builtinHeadersFilter = HeadersSearchPath::isBuiltInHeaders;

    AnalyzerConfiguration.collectDefinesAndIncludes(compilerSettings, properties, builtinHeadersFilter);

    assertEquals("#define DEBUG 1\n#define VERSION 2\n", properties.get("preprocessorDefines"));
    assertEquals("/builtin/include", properties.get("builtinHeaders"));
  }

  @Test
  void testCollectDefinesAndIncludes_withNoMatchingHeaders() {
    var compilerSettings = mock(OCCompilerSettings.class);
    when(compilerSettings.getPreprocessorDefines()).thenReturn(List.of("#define EMPTY"));

    var header = mock(HeadersSearchPath.class);
    when(header.getPath()).thenReturn("/some/path");

    when(compilerSettings.getHeadersSearchPaths()).thenReturn(List.of(header));

    var properties = new HashMap<String, String>();
    Predicate<HeadersSearchPath> noMatchFilter = h -> false;

    AnalyzerConfiguration.collectDefinesAndIncludes(compilerSettings, properties, noMatchFilter);

    assertEquals("#define EMPTY\n", properties.get("preprocessorDefines"));
    assertEquals("", properties.get("builtinHeaders"));
  }

  @Test
  void testCollectDefinesAndIncludes_withEmptyHeadersList() {
    var compilerSettings = mock(OCCompilerSettings.class);
    when(compilerSettings.getPreprocessorDefines()).thenReturn(List.of("#define MINIMAL"));
    when(compilerSettings.getHeadersSearchPaths()).thenReturn(List.of());

    var properties = new HashMap<String, String>();
    Predicate<HeadersSearchPath> anyFilter = h -> true;

    AnalyzerConfiguration.collectDefinesAndIncludes(compilerSettings, properties, anyFilter);

    assertEquals("#define MINIMAL\n", properties.get("preprocessorDefines"));
    assertEquals("", properties.get("builtinHeaders"));
  }

  @Test
  void testCollectDefinesAndIncludes_preservesExistingProperties() {
    var compilerSettings = mock(OCCompilerSettings.class);
    when(compilerSettings.getPreprocessorDefines()).thenReturn(List.of("#define NEW 1"));

    var header = mock(HeadersSearchPath.class);
    when(header.getPath()).thenReturn("/new/include");

    when(compilerSettings.getHeadersSearchPaths()).thenReturn(List.of(header));

    var properties = new HashMap<String, String>();
    properties.put("existingKey", "existingValue");
    Predicate<HeadersSearchPath> allFilter = h -> true;

    AnalyzerConfiguration.collectDefinesAndIncludes(compilerSettings, properties, allFilter);

    assertEquals("#define NEW 1\n", properties.get("preprocessorDefines"));
    assertEquals("/new/include", properties.get("builtinHeaders"));
    assertEquals("existingValue", properties.get("existingKey"));
    assertEquals(3, properties.size());
  }
}
