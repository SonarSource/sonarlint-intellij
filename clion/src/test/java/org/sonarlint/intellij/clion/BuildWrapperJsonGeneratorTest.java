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
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuildWrapperJsonGeneratorTest {

  @Test
  void empty() {
    String json = new BuildWrapperJsonGenerator().build();
    assertEquals("{\"version\":0,\"captures\":[]}", json);
  }

  @Test
  void single() {
    MockLocalFileSystem fileSystem = new MockLocalFileSystem();

    VirtualFile virtualFile = fileSystem.findFileByIoFile(new File("test.cpp"));
    OCCompilerSettings compilerSettings = mock(OCCompilerSettings.class);
    when(compilerSettings.getCompilerExecutable()).thenReturn(new File("/path/to/compiler"));
    when(compilerSettings.getCompilerWorkingDir()).thenReturn(new File("/path/to/compiler/working/dir"));
    CidrCompilerSwitches compilerSwitches = mock(CidrCompilerSwitches.class);
    when(compilerSwitches.getList(CidrCompilerSwitches.Format.RAW)).thenReturn(Arrays.asList("a1", "a2"));
    when(compilerSettings.getCompilerSwitches()).thenReturn(compilerSwitches);

    String json = new BuildWrapperJsonGenerator()
      .add(new AnalyzerConfiguration.Configuration(virtualFile, compilerSettings, "clang", null, false))
      .build();
    assertEquals(
      "{\"version\":0,\"captures\":[" +
        "{\"compiler\":\"clang\",\"cwd\":\"/path/to/compiler/working/dir\",\"executable\":\"/path/to/compiler\",\"cmd\":[\"/path/to/compiler\",\"/test.cpp\",\"a1\",\"a2\"]}" +
        "]}",
      json);
  }

  @Test
  void multiple() {
    MockLocalFileSystem fileSystem = new MockLocalFileSystem();

    VirtualFile virtualFile = fileSystem.findFileByIoFile(new File("test.cpp"));
    OCCompilerSettings compilerSettings = mock(OCCompilerSettings.class);
    when(compilerSettings.getCompilerExecutable()).thenReturn(new File("/path/to/compiler"));
    when(compilerSettings.getCompilerWorkingDir()).thenReturn(new File("/path/to/compiler/working/dir"));
    CidrCompilerSwitches compilerSwitches = mock(CidrCompilerSwitches.class);
    when(compilerSwitches.getList(CidrCompilerSwitches.Format.RAW)).thenReturn(Arrays.asList("a1", "a2"));
    when(compilerSettings.getCompilerSwitches()).thenReturn(compilerSwitches);

    VirtualFile virtualFile2 = fileSystem.findFileByIoFile(new File("test2.cpp"));
    OCCompilerSettings compilerSettings2 = mock(OCCompilerSettings.class);
    when(compilerSettings2.getCompilerExecutable()).thenReturn(new File("/path/to/compiler2"));
    when(compilerSettings2.getCompilerWorkingDir()).thenReturn(new File("/path/to/compiler/working/dir2"));
    CidrCompilerSwitches compilerSwitches2 = mock(CidrCompilerSwitches.class);
    when(compilerSwitches2.getList(CidrCompilerSwitches.Format.RAW)).thenReturn(Arrays.asList("b1", "b2"));
    when(compilerSettings2.getCompilerSwitches()).thenReturn(compilerSwitches2);

    String json = new BuildWrapperJsonGenerator()
      .add(new AnalyzerConfiguration.Configuration(virtualFile, compilerSettings, "clang", null, false))
      .add(new AnalyzerConfiguration.Configuration(virtualFile2, compilerSettings2, "clang", null, false))
      .build();
    assertEquals(
      "{\"version\":0,\"captures\":[" +
        "{\"compiler\":\"clang\",\"cwd\":\"/path/to/compiler/working/dir\",\"executable\":\"/path/to/compiler\",\"cmd\":[\"/path/to/compiler\",\"/test.cpp\",\"a1\",\"a2\"]}," +
        "{\"compiler\":\"clang\",\"cwd\":\"/path/to/compiler/working/dir2\",\"executable\":\"/path/to/compiler2\",\"cmd\":[\"/path/to/compiler2\",\"/test2.cpp\",\"b1\",\"b2\"]}" +
        "]}",
      json);
  }
}
