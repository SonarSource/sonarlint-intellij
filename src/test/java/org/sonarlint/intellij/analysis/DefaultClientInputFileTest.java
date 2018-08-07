/*
 * SonarLint for IntelliJ IDEA
 * Copyright (C) 2015 SonarSource
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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DefaultClientInputFileTest {
  private DefaultClientInputFile inputFile;

  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Test
  public void testNoDoc() throws IOException, URISyntaxException {
    VirtualFile vFile = mock(VirtualFile.class);
    when(vFile.getPath()).thenReturn("file");
    when(vFile.contentsToByteArray()).thenReturn("test string".getBytes(StandardCharsets.UTF_8));
    when(vFile.getInputStream()).thenReturn(new ByteArrayInputStream("test string".getBytes(StandardCharsets.UTF_8)));
    when(vFile.getUrl()).thenReturn("file://file");

    inputFile = new DefaultClientInputFile(vFile, "file", true, StandardCharsets.UTF_8);

    assertThat(inputFile.uri()).isEqualTo(new URI("file://file"));
    assertThat(inputFile.getCharset()).isEqualTo(StandardCharsets.UTF_8);
    assertThat(inputFile.isTest()).isTrue();
    assertThat(inputFile.getPath()).isEqualTo("file");
    assertThat(inputFile.getClientObject()).isEqualTo(vFile);
    assertThat(inputFile.contents()).isEqualTo("test string");
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputFile.inputStream()))) {
      assertThat(reader.lines().collect(Collectors.joining())).isEqualTo("test string");
    }
  }

  @Test
  public void testDoc() throws IOException {
    VirtualFile vFile = mock(VirtualFile.class);
    Document doc = mock(Document.class);
    when(doc.getText()).thenReturn("test string");
    when(vFile.getUrl()).thenReturn("file://foo/Bar.php");
    inputFile = new DefaultClientInputFile(vFile, "foo/Bar.php", true, StandardCharsets.UTF_8, doc);

    assertThat(inputFile.contents()).isEqualTo("test string");
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputFile.inputStream()))) {
      assertThat(reader.lines().collect(Collectors.joining())).isEqualTo("test string");
    }
  }
}
