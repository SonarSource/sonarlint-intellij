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
package org.sonarlint.intellij.analysis;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarlint.intellij.AbstractSonarLintLightTests;
import org.sonarlint.intellij.common.analysis.ForcedLanguage;
import org.sonarsource.sonarlint.core.commons.api.SonarLanguage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultClientInputFileTests extends AbstractSonarLintLightTests {
  private DefaultClientInputFile inputFile;
  private VirtualFile vFile;
  private File file;

  @BeforeEach
  void prepare(@TempDir Path tempDirPath) throws IOException {
    file = new File(Files.createDirectories(tempDirPath.resolve("My Projects")).toString(), "/sonar-clirr/src/main/java/org/sonar/plugins/clirr/ClirrSensor.java");
    file.getParentFile().mkdirs();
    file.createNewFile();
    vFile = mock(VirtualFile.class);
    var path = FileUtil.toSystemIndependentName(file.getAbsolutePath());
    when(vFile.getPath()).thenReturn(path);
    // SLI-379 Mocking the true implementation, in case we try to use it, to see how it is broken
    when(vFile.getUrl()).thenReturn(VirtualFileManager.constructUrl("file", path));
    when(vFile.isInLocalFileSystem()).thenReturn(true);
  }

  @Test
  void testNoBuffer() throws IOException {
    when(vFile.getInputStream()).thenAnswer(i -> new ByteArrayInputStream("test string".getBytes(StandardCharsets.UTF_8)));

    inputFile = new DefaultClientInputFile(vFile, "unused", true, StandardCharsets.UTF_8, ForcedLanguage.CPP);

    assertThat(inputFile.uri()).isEqualTo(file.toURI());
    assertThat(inputFile.uri().toString()).endsWith("/My%20Projects/sonar-clirr/src/main/java/org/sonar/plugins/clirr/ClirrSensor.java");
    assertThat(inputFile.getCharset()).isEqualTo(StandardCharsets.UTF_8);
    assertThat(inputFile.isTest()).isTrue();
    assertThat(inputFile.getPath()).endsWith("/My Projects/sonar-clirr/src/main/java/org/sonar/plugins/clirr/ClirrSensor.java");
    assertThat(Paths.get(inputFile.getPath())).isEqualTo(file.toPath());
    assertThat(inputFile.getClientObject()).isEqualTo(vFile);
    assertThat(inputFile.contents()).isEqualTo("test string");
    assertThat(inputFile.language()).isEqualTo(SonarLanguage.CPP);
    try (var reader = new BufferedReader(new InputStreamReader(inputFile.inputStream()))) {
      assertThat(reader.lines().collect(Collectors.joining())).isEqualTo("test string");
    }
  }

  @Test
  void testContentFromBuffer() throws IOException {
    inputFile = new DefaultClientInputFile(vFile, "unused", true, StandardCharsets.UTF_8, "test string", 0, null);

    assertThat(inputFile.contents()).isEqualTo("test string");
    assertThat(inputFile.language()).isNull();
    try (var reader = new BufferedReader(new InputStreamReader(inputFile.inputStream()))) {
      assertThat(reader.lines().collect(Collectors.joining())).isEqualTo("test string");
    }
  }

  @Test
  void testUriRoundTrip() {
    inputFile = new DefaultClientInputFile(vFile, "unused", true, StandardCharsets.UTF_8, null);

    // This check is important to have the URIPredicate working (see SLI-379)
    assertThat(Paths.get(inputFile.getPath()).toUri()).isEqualTo(inputFile.uri());
  }
}
