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

import com.intellij.mock.MockLocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildWrapperJsonGeneratorTest {

  @Test
  void empty() {
    String json = new BuildWrapperJsonGenerator().build();
    assertEquals("{\"version\":0,\"captures\":[]}", json);
  }

  @Test
  void single() {
    MockLocalFileSystem fileSystem = new MockLocalFileSystem();

    VirtualFile virtualFile = fileSystem.findFileByPath(new File("test.cpp").getPath());
    File compilerExecutable = new File("/path/to/compiler").getAbsoluteFile();
    File compilerWorkingDir = new File("/path/to/compiler/working/dir").getAbsoluteFile();

    AnalyzerConfiguration.Configuration configuration = new AnalyzerConfiguration.Configuration(
      virtualFile,
      compilerExecutable.toString(),
      compilerWorkingDir.toString(),
      Arrays.asList("a1", "a2"),
      "clang",
      null,
      false);
    String json = new BuildWrapperJsonGenerator()
      .add(configuration)
      .build();
    assertEquals(
      "{\"version\":0,\"captures\":[" +
        "{\"compiler\":\"clang\",\"cwd\":"
        + quote(compilerWorkingDir)
        + ",\"executable\":"
        + quote(compilerExecutable)
        + ",\"cmd\":["
        + quote(compilerExecutable) + ",\"/test.cpp\",\"a1\",\"a2\"]}" +
        "]}",
      json);
  }

  @Test
  void multiple() {
    MockLocalFileSystem fileSystem = new MockLocalFileSystem();

    VirtualFile virtualFile = fileSystem.findFileByPath(new File("test.cpp").getPath());
    File compilerExecutable = new File("/path/to/compiler").getAbsoluteFile();
    File compilerWorkingDir = new File("/path/to/compiler/working/dir").getAbsoluteFile();

    VirtualFile virtualFile2 = fileSystem.findFileByPath(new File("test2.cpp").getPath());
    File compilerExecutable2 = new File("/path/to/compiler2").getAbsoluteFile();
    File compilerWorkingDir2 = new File("/path/to/compiler/working/dir2").getAbsoluteFile();

    VirtualFile virtualFile3 = fileSystem.findFileByPath(new File("test3.h").getPath());
    File compilerExecutable3 = new File("/path/to/compiler3").getAbsoluteFile();
    File compilerWorkingDir3 = new File("/path/to/compiler/working/dir3").getAbsoluteFile();

    AnalyzerConfiguration.Configuration configuration1 = new AnalyzerConfiguration.Configuration(
      virtualFile,
      compilerExecutable.toString(),
      compilerWorkingDir.toString(),
      Arrays.asList("a1", "a2"),
      "clang",
      null,
      false);
    AnalyzerConfiguration.Configuration configuration2 = new AnalyzerConfiguration.Configuration(
      virtualFile2,
      compilerExecutable2.toString(),
      compilerWorkingDir2.toString(),
      Arrays.asList("b1", "b2"),
      "clang",
      null,
      false);
    AnalyzerConfiguration.Configuration configuration3 = new AnalyzerConfiguration.Configuration(
      virtualFile3,
      compilerExecutable3.toString(),
      compilerWorkingDir3.toString(),
      Arrays.asList("c1", "c2"),
      "clang",
      null,
      true);
    String json = new BuildWrapperJsonGenerator()
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
        + ",\"cmd\":["
        + quote(compilerExecutable) + ",\"/test.cpp\",\"a1\",\"a2\"]}"
        + ","
        + "{\"compiler\":\"clang\",\"cwd\":"
        + quote(compilerWorkingDir2)
        + ",\"executable\":"
        + quote(compilerExecutable2)
        + ",\"cmd\":["
        + quote(compilerExecutable2) + ",\"/test2.cpp\",\"b1\",\"b2\"]}"
        + ","
        + "{\"compiler\":\"clang\",\"cwd\":"
        + quote(compilerWorkingDir3)
        + ",\"executable\":"
        + quote(compilerExecutable3)
        + ",\"properties\":{\"isHeaderFile\":\"true\"}"
        + ",\"cmd\":["
        + quote(compilerExecutable3) + ",\"/test3.h\",\"c1\",\"c2\"]}"
        + "]}",
      json);
  }

  private static String quote(File file) {
    return BuildWrapperJsonGenerator.quote(file.getAbsoluteFile().toString());
  }
}
