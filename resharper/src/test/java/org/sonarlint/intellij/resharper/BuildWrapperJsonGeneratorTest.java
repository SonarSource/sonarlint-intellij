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

import com.intellij.mock.MockLocalFileSystem;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildWrapperJsonGeneratorTest {

  @Test
  void empty() {
    var json = new BuildWrapperJsonGenerator().build();
    assertEquals("{\"version\":0,\"captures\":[]}", json);
  }

  @Test
  void single() {
    var fileSystem = new MockLocalFileSystem();

    var virtualFile = fileSystem.findFileByIoFile(new File("test.cpp"));
    var compilerExecutable = new File("/path/to/compiler").getAbsoluteFile();
    var compilerWorkingDir = new File("/path/to/compiler/working/dir").getAbsoluteFile();

    var properties = new TreeMap<String, String>();
    properties.put("prop1", "val1");
    properties.put("prop2", "\"val2\"");
    properties.put("propn", "valn");

    var configuration = new AnalyzerConfiguration.Configuration(
      virtualFile,
      compilerExecutable.toString(),
      compilerWorkingDir.toString(),
      List.of("a1", "a2"),
      "clang",
      null,
      properties);
    var json = new BuildWrapperJsonGenerator()
      .add(configuration)
      .build();
    assertEquals(
      "{\"version\":0,\"captures\":[" +
        "{\"compiler\":\"clang\",\"cwd\":"
        + quote(compilerWorkingDir)
        + ",\"executable\":"
        + quote(compilerExecutable)
        + ",\"properties\":{\"prop1\":\"val1\",\"prop2\":\"\\\"val2\\\"\",\"propn\":\"valn\"}"
        + ",\"cmd\":["
        + quote(compilerExecutable) + ",\"" + virtualFile.getCanonicalPath() + "\",\"a1\",\"a2\"]}" +
        "]}",
      json);
  }

  @Test
  void multiple() {
    var fileSystem = new MockLocalFileSystem();

    var virtualFile = fileSystem.findFileByIoFile(new File("test.cpp"));
    var compilerExecutable = new File("/path/to/compiler").getAbsoluteFile();
    var compilerWorkingDir = new File("/path/to/compiler/working/dir").getAbsoluteFile();

    var virtualFile2 = fileSystem.findFileByIoFile(new File("test2.cpp"));
    var compilerExecutable2 = new File("/path/to/compiler2").getAbsoluteFile();
    var compilerWorkingDir2 = new File("/path/to/compiler/working/dir2").getAbsoluteFile();

    var virtualFile3 = fileSystem.findFileByIoFile(new File("test3.h"));
    var compilerExecutable3 = new File("/path/to/compiler3").getAbsoluteFile();
    var compilerWorkingDir3 = new File("/path/to/compiler/working/dir3").getAbsoluteFile();

    var configuration1 = new AnalyzerConfiguration.Configuration(
      virtualFile,
      compilerExecutable.toString(),
      compilerWorkingDir.toString(),
      List.of("a1", "a2"),
      "clang",
      null,
      Map.of("isHeaderFile", "false"));
    var properties2 = new TreeMap<String, String>();
    properties2.put("prop1", "val1");
    properties2.put("prop2", "\"val2\"");
    properties2.put("propn", "valn");
    var configuration2 = new AnalyzerConfiguration.Configuration(
      virtualFile2,
      compilerExecutable2.toString(),
      compilerWorkingDir2.toString(),
      List.of("b1", "b2"),
      "clang",
      null,
      properties2);
    var configuration3 = new AnalyzerConfiguration.Configuration(
      virtualFile3,
      compilerExecutable3.toString(),
      compilerWorkingDir3.toString(),
      List.of("c1", "c2"),
      "clang",
      null,
      Collections.emptyMap());
    var json = new BuildWrapperJsonGenerator()
      .add(configuration1)
      .add(configuration2)
      .add(configuration3)
      .build();
    assertEquals(
      "{\"version\":0,\"captures\":[" +
        "{\"compiler\":\"clang\",\"cwd\":"
        + quote(compilerWorkingDir)
        + ",\"executable\":"
        + quote(compilerExecutable)
        + ",\"properties\":{\"isHeaderFile\":\"false\"}"
        + ",\"cmd\":["
        + quote(compilerExecutable) + ",\"" + virtualFile.getCanonicalPath() + "\",\"a1\",\"a2\"]}"
        + ","
        + "{\"compiler\":\"clang\",\"cwd\":"
        + quote(compilerWorkingDir2)
        + ",\"executable\":"
        + quote(compilerExecutable2)
        + ",\"properties\":{\"prop1\":\"val1\",\"prop2\":\"\\\"val2\\\"\",\"propn\":\"valn\"}"
        + ",\"cmd\":["
        + quote(compilerExecutable2) + ",\"" + virtualFile2.getCanonicalPath() + "\",\"b1\",\"b2\"]}"
        + ","
        + "{\"compiler\":\"clang\",\"cwd\":"
        + quote(compilerWorkingDir3)
        + ",\"executable\":"
        + quote(compilerExecutable3)
        + ",\"properties\":{}"
        + ",\"cmd\":["
        + quote(compilerExecutable3) + ",\"" + virtualFile3.getCanonicalPath() + "\",\"c1\",\"c2\"]}"
        + "]}",
      json);
  }

  private static String quote(File file) {
    return BuildWrapperJsonGenerator.quote(file.getAbsoluteFile().toString());
  }
}
